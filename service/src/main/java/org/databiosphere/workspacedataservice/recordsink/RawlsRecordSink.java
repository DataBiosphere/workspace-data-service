package org.databiosphere.workspacedataservice.recordsink;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mu.util.stream.BiStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.databiosphere.workspacedataservice.dataimport.ImportDetails;
import org.databiosphere.workspacedataservice.pubsub.PubSub;
import org.databiosphere.workspacedataservice.recordsink.RawlsModel.AddListMember;
import org.databiosphere.workspacedataservice.recordsink.RawlsModel.AddUpdateAttribute;
import org.databiosphere.workspacedataservice.recordsink.RawlsModel.AttributeOperation;
import org.databiosphere.workspacedataservice.recordsink.RawlsModel.CreateAttributeValueList;
import org.databiosphere.workspacedataservice.recordsink.RawlsModel.Entity;
import org.databiosphere.workspacedataservice.recordsink.RawlsModel.RemoveAttribute;
import org.databiosphere.workspacedataservice.service.model.DataTypeMapping;
import org.databiosphere.workspacedataservice.shared.model.Record;
import org.databiosphere.workspacedataservice.shared.model.RecordType;
import org.databiosphere.workspacedataservice.storage.GcsStorage;

/**
 * {@link RecordSink} implementation that produces Rawls-compatible JSON using {@link RawlsModel}
 * serialization classes.
 */
public class RawlsRecordSink implements RecordSink {
  private final RawlsAttributePrefixer attributePrefixer;
  private final ObjectMapper mapper;
  private final GcsStorage storage;
  private final PubSub pubSub;
  private final ImportDetails importDetails;

  RawlsRecordSink(
      RawlsAttributePrefixer attributePrefixer,
      ObjectMapper mapper,
      GcsStorage storage,
      PubSub pubSub,
      ImportDetails importDetails) {
    this.attributePrefixer = attributePrefixer;
    this.mapper = mapper;
    this.storage = storage;
    this.pubSub = pubSub;
    this.importDetails = importDetails;
  }

  public static RawlsRecordSink create(
      ObjectMapper mapper, GcsStorage storage, PubSub pubSub, ImportDetails importDetails) {
    return new RawlsRecordSink(
        new RawlsAttributePrefixer(importDetails.prefixStrategy()),
        mapper,
        storage,
        pubSub,
        importDetails);
  }

  @Override
  public Map<String, DataTypeMapping> createOrModifyRecordType(
      RecordType recordType,
      Map<String, DataTypeMapping> schema,
      List<Record> records,
      String primaryKey) {
    // This is a no-op for Rawls as the schema changes occur as a side effect of writeBatch
    return schema;
  }

  @Override
  public void upsertBatch(
      RecordType recordType,
      Map<String, DataTypeMapping> schema, // ignored
      List<Record> records,
      String primaryKey // ignored
      ) throws IOException {
    ImmutableList.Builder<Entity> entities = ImmutableList.builder();
    records.stream().map(this::toEntity).forEach(entities::add);

    String upsertFileName =
        storage.createGcsFile(
            new ByteArrayInputStream(mapper.writeValueAsBytes(entities.build())),
            requireNonNull(importDetails.jobId()));

    publishToPubSub(
        importDetails.collectionId(),
        requireNonNull(importDetails.userEmail()),
        importDetails.jobId(),
        upsertFileName);
  }

  @Override
  public void deleteBatch(RecordType recordType, List<Record> records) {
    throw new UnsupportedOperationException("RawlsRecordSink does not support deleteBatch");
  }

  @Override
  public void close() {
    // currently a no-op
    // TODO(AJ-1669): do pubsub here, maybe do GCS cleanup here (assuming a file was reused
    //   throughout)
  }

  private void publishToPubSub(UUID workspaceId, String user, UUID jobId, String upsertFile) {
    Map<String, String> message =
        new ImmutableMap.Builder<String, String>()
            .put("workspaceId", workspaceId.toString())
            .put("userEmail", user)
            .put("jobId", jobId.toString())
            .put("upsertFile", storage.getBucketName() + "/" + upsertFile)
            .put("isUpsert", "true")
            .put("isCWDS", "true")
            .build();
    pubSub.publishSync(message);
  }

  private Entity toEntity(Record record) {
    return new Entity(record.getId(), record.getRecordType().toString(), makeOperations(record));
  }

  private List<? extends AttributeOperation> makeOperations(Record record) {
    return BiStream.from(record.getAttributes().attributeSet())
        .mapKeys(
            attributeName ->
                attributePrefixer.prefix(attributeName, record.getRecordType().getName()))
        .filterValues(Objects::nonNull)
        .flatMapToObj(this::toOperations)
        .toList();
  }

  private Stream<? extends AttributeOperation> toOperations(String name, Object attributeValue) {
    if (attributeValue instanceof List<?> values) {
      return Stream.concat(
          Stream.of(new RemoveAttribute(name), new CreateAttributeValueList(name)),
          values.stream().map(value -> new AddListMember(name, value)));
    }

    return Stream.of(new AddUpdateAttribute(name, attributeValue));
  }
}

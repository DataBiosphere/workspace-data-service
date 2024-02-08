package org.databiosphere.workspacedataservice.service;

import static org.databiosphere.workspacedataservice.service.RecordUtils.validateVersion;
import static org.databiosphere.workspacedataservice.service.model.ReservedNames.RECORD_ID;

import bio.terra.common.db.ReadTransaction;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.databiosphere.workspacedataservice.activitylog.ActivityLogger;
import org.databiosphere.workspacedataservice.dao.RecordDao;
import org.databiosphere.workspacedataservice.recordstream.PrimaryKeyResolver;
import org.databiosphere.workspacedataservice.recordstream.StreamingWriteHandler;
import org.databiosphere.workspacedataservice.recordstream.StreamingWriteHandlerFactory;
import org.databiosphere.workspacedataservice.recordstream.TsvStreamWriteHandler;
import org.databiosphere.workspacedataservice.recordstream.TwoPassStreamingWriteHandler.ImportMode;
import org.databiosphere.workspacedataservice.sam.SamDao;
import org.databiosphere.workspacedataservice.service.model.AttributeSchema;
import org.databiosphere.workspacedataservice.service.model.BatchWriteResult;
import org.databiosphere.workspacedataservice.service.model.DataTypeMapping;
import org.databiosphere.workspacedataservice.service.model.RecordTypeSchema;
import org.databiosphere.workspacedataservice.service.model.Relation;
import org.databiosphere.workspacedataservice.service.model.exception.AuthorizationException;
import org.databiosphere.workspacedataservice.service.model.exception.BadStreamingWriteRequestException;
import org.databiosphere.workspacedataservice.service.model.exception.ConflictException;
import org.databiosphere.workspacedataservice.service.model.exception.MissingObjectException;
import org.databiosphere.workspacedataservice.service.model.exception.ValidationException;
import org.databiosphere.workspacedataservice.shared.model.Record;
import org.databiosphere.workspacedataservice.shared.model.RecordQueryResponse;
import org.databiosphere.workspacedataservice.shared.model.RecordRequest;
import org.databiosphere.workspacedataservice.shared.model.RecordResponse;
import org.databiosphere.workspacedataservice.shared.model.RecordType;
import org.databiosphere.workspacedataservice.shared.model.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class RecordOrchestratorService { // TODO give me a better name

  private static final Logger LOGGER = LoggerFactory.getLogger(RecordOrchestratorService.class);
  private static final int MAX_RECORDS = 1_000;

  private final RecordDao recordDao;
  private final StreamingWriteHandlerFactory streamingWriteHandlerFactory;
  private final BatchWriteService batchWriteService;
  private final RecordService recordService;
  private final InstanceService instanceService;
  private final SamDao samDao;
  private final ActivityLogger activityLogger;

  private final TsvSupport tsvSupport;

  public RecordOrchestratorService(
      RecordDao recordDao,
      StreamingWriteHandlerFactory streamingWriteHandlerFactory,
      BatchWriteService batchWriteService,
      RecordService recordService,
      InstanceService instanceService,
      SamDao samDao,
      ActivityLogger activityLogger,
      TsvSupport tsvSupport) {
    this.recordDao = recordDao;
    this.streamingWriteHandlerFactory = streamingWriteHandlerFactory;
    this.batchWriteService = batchWriteService;
    this.recordService = recordService;
    this.instanceService = instanceService;
    this.samDao = samDao;
    this.activityLogger = activityLogger;
    this.tsvSupport = tsvSupport;
  }

  public RecordResponse updateSingleRecord(
      UUID instanceId,
      String version,
      RecordType recordType,
      String recordId,
      RecordRequest recordRequest) {
    validateAndPermissions(instanceId, version);
    checkRecordTypeExists(instanceId, recordType);
    RecordResponse response =
        recordService.updateSingleRecord(instanceId, recordType, recordId, recordRequest);
    activityLogger.saveEventForCurrentUser(
        user -> user.updated().record().withRecordType(recordType).withId(recordId));
    return response;
  }

  public void validateAndPermissions(UUID instanceId, String version) {
    validateVersion(version);
    instanceService.validateInstance(instanceId);

    boolean hasWriteInstancePermission = samDao.hasWriteInstancePermission();
    LOGGER.debug("hasWriteInstancePermission? {}", hasWriteInstancePermission);

    if (!hasWriteInstancePermission) {
      throw new AuthorizationException("Caller does not have permission to write to instance.");
    }
  }

  @ReadTransaction
  public RecordResponse getSingleRecord(
      UUID instanceId, String version, RecordType recordType, String recordId) {
    validateVersion(version);
    instanceService.validateInstance(instanceId);
    checkRecordTypeExists(instanceId, recordType);
    Record result =
        recordDao
            .getSingleRecord(instanceId, recordType, recordId)
            .orElseThrow(() -> new MissingObjectException("Record"));
    return new RecordResponse(recordId, recordType, result.getAttributes());
  }

  // N.B. transaction annotated in batchWriteService.batchWrite
  public int tsvUpload(
      UUID instanceId,
      String version,
      RecordType recordType,
      Optional<String> primaryKey,
      MultipartFile records)
      throws IOException {
    validateAndPermissions(instanceId, version);
    if (recordDao.recordTypeExists(instanceId, recordType)) {
      primaryKey =
          Optional.of(recordService.validatePrimaryKey(instanceId, recordType, primaryKey));
    }

    TsvStreamWriteHandler streamingWriteHandler =
        streamingWriteHandlerFactory.forTsv(records.getInputStream(), recordType, primaryKey);
    BatchWriteResult result =
        batchWriteService.batchWrite(
            streamingWriteHandler,
            instanceId,
            recordType,
            // the extra cast here isn't exactly necessary, but left here to call out the additional
            // tangential responsibility of the TsvStreamWriteHandler; this can be removed if we
            // can converge on using PrimaryKeyResolver more generally across all formats.
            ((PrimaryKeyResolver) streamingWriteHandler).getPrimaryKey(),
            ImportMode.BASE_ATTRIBUTES);
    int qty = result.getUpdatedCount(recordType);
    activityLogger.saveEventForCurrentUser(
        user -> user.upserted().record().withRecordType(recordType).ofQuantity(qty));
    return qty;
  }

  // TODO: enable read transaction
  public StreamingResponseBody streamAllEntities(
      UUID instanceId, String version, RecordType recordType) {
    validateVersion(version);
    instanceService.validateInstance(instanceId);
    checkRecordTypeExists(instanceId, recordType);
    List<String> headers = recordDao.getAllAttributeNames(instanceId, recordType);

    Map<String, DataTypeMapping> typeSchema =
        recordDao.getExistingTableSchema(instanceId, recordType);

    return httpResponseOutputStream -> {
      try (Stream<Record> allRecords = recordDao.streamAllRecordsForType(instanceId, recordType)) {
        tsvSupport.writeTsvToStream(allRecords, typeSchema, httpResponseOutputStream, headers);
      }
    };
  }

  @ReadTransaction
  public RecordQueryResponse queryForRecords(
      UUID instanceId, RecordType recordType, String version, SearchRequest searchRequest) {
    validateVersion(version);
    instanceService.validateInstance(instanceId);
    checkRecordTypeExists(instanceId, recordType);
    if (null == searchRequest) {
      searchRequest = new SearchRequest();
    }
    if (searchRequest.getLimit() > MAX_RECORDS
        || searchRequest.getLimit() < 1
        || searchRequest.getOffset() < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Limit must be more than 0 and can't exceed "
              + MAX_RECORDS
              + ", and offset must be positive.");
    }
    if (searchRequest.getSortAttribute() != null
        && !recordDao
            .getExistingTableSchemaLessPrimaryKey(instanceId, recordType)
            .containsKey(searchRequest.getSortAttribute())) {
      throw new MissingObjectException("Requested sort attribute");
    }
    int totalRecords = recordDao.countRecords(instanceId, recordType);
    if (searchRequest.getOffset() > totalRecords) {
      return new RecordQueryResponse(searchRequest, Collections.emptyList(), totalRecords);
    }
    LOGGER.info("queryForEntities: {}", recordType.getName());
    List<Record> records =
        recordDao.queryForRecords(
            recordType,
            searchRequest.getLimit(),
            searchRequest.getOffset(),
            searchRequest.getSort().name().toLowerCase(),
            searchRequest.getSortAttribute(),
            instanceId);
    List<RecordResponse> recordList =
        records.stream()
            .map(r -> new RecordResponse(r.getId(), r.getRecordType(), r.getAttributes()))
            .toList();
    return new RecordQueryResponse(searchRequest, recordList, totalRecords);
  }

  public ResponseEntity<RecordResponse> upsertSingleRecord(
      UUID instanceId,
      String version,
      RecordType recordType,
      String recordId,
      Optional<String> primaryKey,
      RecordRequest recordRequest) {
    validateAndPermissions(instanceId, version);
    ResponseEntity<RecordResponse> response =
        recordService.upsertSingleRecord(
            instanceId, recordType, recordId, primaryKey, recordRequest);

    if (response.getStatusCode() == HttpStatus.CREATED) {
      activityLogger.saveEventForCurrentUser(
          user -> user.created().record().withRecordType(recordType).withId(recordId));
    } else {
      activityLogger.saveEventForCurrentUser(
          user -> user.updated().record().withRecordType(recordType).withId(recordId));
    }
    return response;
  }

  public boolean deleteSingleRecord(
      UUID instanceId, String version, RecordType recordType, String recordId) {
    validateAndPermissions(instanceId, version);
    checkRecordTypeExists(instanceId, recordType);
    boolean response = recordService.deleteSingleRecord(instanceId, recordType, recordId);
    activityLogger.saveEventForCurrentUser(
        user -> user.deleted().record().withRecordType(recordType).withId(recordId));
    return response;
  }

  public void deleteRecordType(UUID instanceId, String version, RecordType recordType) {
    validateAndPermissions(instanceId, version);
    checkRecordTypeExists(instanceId, recordType);
    recordService.deleteRecordType(instanceId, recordType);
    activityLogger.saveEventForCurrentUser(
        user -> user.deleted().table().ofQuantity(1).withRecordType(recordType));
  }

  public void renameAttribute(
      UUID instanceId,
      String version,
      RecordType recordType,
      String attribute,
      String newAttributeName) {
    validateAndPermissions(instanceId, version);
    checkRecordTypeExists(instanceId, recordType);
    validateRenameAttribute(instanceId, recordType, attribute, newAttributeName);
    recordService.renameAttribute(instanceId, recordType, attribute, newAttributeName);
    activityLogger.saveEventForCurrentUser(
        user ->
            user.renamed()
                .attribute()
                .withRecordType(recordType)
                .withIds(new String[] {attribute, newAttributeName}));
  }

  private void validateRenameAttribute(
      UUID instanceId, RecordType recordType, String attribute, String newAttributeName) {
    RecordTypeSchema schema = getSchemaDescription(instanceId, recordType);

    if (schema.isPrimaryKey(attribute)) {
      throw new ValidationException("Unable to rename primary key attribute");
    }
    if (!schema.containsAttribute(attribute)) {
      throw new MissingObjectException("Attribute");
    }
    if (schema.containsAttribute(newAttributeName)) {
      throw new ConflictException("Attribute already exists");
    }
  }

  public void updateAttributeDataType(
      UUID instanceId,
      String version,
      RecordType recordType,
      String attribute,
      String newDataType) {
    validateAndPermissions(instanceId, version);
    checkRecordTypeExists(instanceId, recordType);
    RecordTypeSchema schema = getSchemaDescription(instanceId, recordType);
    if (schema.isPrimaryKey(attribute)) {
      throw new ValidationException("Unable to update primary key attribute");
    }

    DataTypeMapping newDataTypeMapping = validateAttributeDataType(newDataType);
    try {
      recordService.updateAttributeDataType(instanceId, recordType, attribute, newDataTypeMapping);
    } catch (IllegalArgumentException e) {
      throw new ValidationException(e.getMessage());
    }
    activityLogger.saveEventForCurrentUser(
        user -> user.updated().attribute().withRecordType(recordType).withId(attribute));
  }

  private DataTypeMapping validateAttributeDataType(String dataType) {
    try {
      return DataTypeMapping.valueOf(dataType);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Invalid datatype");
    }
  }

  public void deleteAttribute(
      UUID instanceId, String version, RecordType recordType, String attribute) {
    validateAndPermissions(instanceId, version);
    checkRecordTypeExists(instanceId, recordType);
    validateDeleteAttribute(instanceId, recordType, attribute);
    recordService.deleteAttribute(instanceId, recordType, attribute);
    activityLogger.saveEventForCurrentUser(
        user -> user.deleted().attribute().withRecordType(recordType).withId(attribute));
  }

  private void validateDeleteAttribute(UUID instanceId, RecordType recordType, String attribute) {
    RecordTypeSchema schema = getSchemaDescription(instanceId, recordType);

    if (schema.isPrimaryKey(attribute)) {
      throw new ValidationException("Unable to delete primary key attribute");
    }
    if (!schema.containsAttribute(attribute)) {
      throw new MissingObjectException("Attribute");
    }
  }

  @ReadTransaction
  public RecordTypeSchema describeRecordType(
      UUID instanceId, String version, RecordType recordType) {
    validateVersion(version);
    instanceService.validateInstance(instanceId);
    checkRecordTypeExists(instanceId, recordType);
    return getSchemaDescription(instanceId, recordType);
  }

  @ReadTransaction
  public List<RecordTypeSchema> describeAllRecordTypes(UUID instanceId, String version) {
    validateVersion(version);
    instanceService.validateInstance(instanceId);
    List<RecordType> allRecordTypes = recordDao.getAllRecordTypes(instanceId);
    return allRecordTypes.stream()
        .map(recordType -> getSchemaDescription(instanceId, recordType))
        .toList();
  }

  public int streamingWrite(
      UUID instanceId,
      String version,
      RecordType recordType,
      Optional<String> primaryKey,
      InputStream is) {
    validateAndPermissions(instanceId, version);
    if (recordDao.recordTypeExists(instanceId, recordType)) {
      recordService.validatePrimaryKey(instanceId, recordType, primaryKey);
    }

    StreamingWriteHandler streamingWriteHandler = null;
    try {
      streamingWriteHandler = streamingWriteHandlerFactory.forJson(is);
    } catch (IOException e) {
      throw new BadStreamingWriteRequestException(e);
    }

    BatchWriteResult result =
        batchWriteService.batchWrite(
            streamingWriteHandler,
            instanceId,
            recordType,
            primaryKey.orElse(RECORD_ID),
            ImportMode.BASE_ATTRIBUTES);
    int qty = result.getUpdatedCount(recordType);
    activityLogger.saveEventForCurrentUser(
        user -> user.modified().record().withRecordType(recordType).ofQuantity(qty));
    return qty;
  }

  private void checkRecordTypeExists(UUID instanceId, RecordType recordType) {
    if (!recordDao.recordTypeExists(instanceId, recordType)) {
      throw new MissingObjectException("Record type");
    }
  }

  private RecordTypeSchema getSchemaDescription(UUID instanceId, RecordType recordType) {
    Map<String, DataTypeMapping> schema = recordDao.getExistingTableSchema(instanceId, recordType);
    List<Relation> relationCols = recordDao.getRelationArrayCols(instanceId, recordType);
    relationCols.addAll(recordDao.getRelationCols(instanceId, recordType));
    Map<String, RecordType> relations =
        relationCols.stream()
            .collect(Collectors.toMap(Relation::relationColName, Relation::relationRecordType));
    List<AttributeSchema> attrSchema =
        schema.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(
                entry ->
                    new AttributeSchema(
                        entry.getKey(), entry.getValue().toString(), relations.get(entry.getKey())))
            .toList();
    int recordCount = recordDao.countRecords(instanceId, recordType);
    return new RecordTypeSchema(
        recordType, attrSchema, recordCount, recordDao.getPrimaryKeyColumn(recordType, instanceId));
  }
}

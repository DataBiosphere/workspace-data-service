package org.databiosphere.workspacedataservice.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.databiosphere.workspacedataservice.service.RelationUtils;
import org.databiosphere.workspacedataservice.shared.model.RecordAttributes;
import org.databiosphere.workspacedataservice.shared.model.RecordId;
import org.databiosphere.workspacedataservice.shared.model.RecordRequest;
import org.databiosphere.workspacedataservice.shared.model.RecordType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
public class RecordControllerMockMvcTest {

  private final ObjectMapper mapper = new ObjectMapper();
  @Autowired private MockMvc mockMvc;

  private static UUID instanceId;

  private static String versionId = "v0.2";

  @BeforeAll
  private static void createWorkspace() {
    instanceId = UUID.randomUUID();
  }

  @Test
  @Transactional
  public void createInstanceAndTryToCreateAgain() throws Exception {
    UUID uuid = UUID.randomUUID();
    mockMvc
        .perform(post("/{instanceId}/{version}/", uuid, versionId))
        .andExpect(status().isCreated());
    mockMvc
        .perform(post("/{instanceId}/{version}/", uuid, versionId))
        .andExpect(status().isConflict());
  }

  @Test
  @Transactional
  public void tryFetchingMissingEntityType() throws Exception {
    mockMvc
        .perform(
            get(
                "/{instanceId}/entities/{versionId}/{recordType}/{entityId}",
                instanceId,
                versionId,
                "missing",
                "missing-2"))
        .andExpect(status().isNotFound());
  }

  @Test
  @Transactional
  public void tryFetchingMissingEntity() throws Exception {
    String recordType1 = "recordType1";
    createSomeRecords(recordType1, 1);
    mockMvc
        .perform(
            get(
                "/{instanceId}/entities/{versionId}/{recordType}/{entityId}",
                instanceId,
                versionId,
                recordType1,
                "missing-2"))
        .andExpect(status().isNotFound());
  }

  @Test
  @Transactional
  public void createAndRetrieveRecord() throws Exception {
    String recordType = "samples";
    createSomeRecords(recordType, 1);
    mockMvc
        .perform(
            get(
                "/{instanceId}/records/{version}/{recordType}/{entityId}",
                instanceId,
                versionId,
                recordType,
                "record_0"))
        .andExpect(status().isOk());
  }

  @Test
  @Transactional
  public void createEntityWithReferences() throws Exception {
    String referencedType = "ref_participants";
    String referringType = "ref_samples";
    createSomeRecords(referencedType, 3);
    createSomeRecords(referringType, 1);
    Map<String, Object> attributes = new HashMap<>();
    String ref = RelationUtils.createRelationString(referencedType, "record_0");
    attributes.put("sample-ref", ref);
    mockMvc
        .perform(
            patch(
                    "/{instanceId}/records/{version}/{recordType}/{entityId}",
                    instanceId,
                    versionId,
                    referringType,
                    "record_0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        new RecordRequest(
                            new RecordId("record_0"),
                            new RecordType(referringType),
                            new RecordAttributes(attributes)))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(ref)));
  }

  @Test
  @Transactional
  public void referencingMissingTableFails() throws Exception {
    String referencedType = "missing";
    String referringType = "ref_samples-2";
    createSomeRecords(referringType, 1);
    Map<String, Object> attributes = new HashMap<>();
    String ref = RelationUtils.createRelationString(referencedType, "record_0");
    attributes.put("sample-ref", ref);
    mockMvc
        .perform(
            put(
                    "/{instanceId}/records/{version}/{recordType}/{entityId}",
                    instanceId,
                    versionId,
                    referringType,
                    "record_0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        new RecordRequest(
                            new RecordId("record_0"),
                            new RecordType(referringType),
                            new RecordAttributes(attributes)))))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .string(
                    containsString(
                        "It looks like you're attempting to assign a reference to a table, missing, that does not exist")));
    ;
  }

  @Test
  @Transactional
  public void referencingMissingEntityFails() throws Exception {
    String referencedType = "ref_participants-2";
    String referringType = "ref_samples-3";
    createSomeRecords(referencedType, 3);
    createSomeRecords(referringType, 1);
    Map<String, Object> attributes = new HashMap<>();
    String ref = RelationUtils.createRelationString(referencedType, "record_99");
    attributes.put("sample-ref", ref);
    mockMvc
        .perform(
            put(
                    "/{instanceId}/records/{version}/{recordType}/{entityId}",
                    instanceId,
                    versionId,
                    referringType,
                    "record_0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        new RecordRequest(
                            new RecordId("entity_0"),
                            new RecordType(referringType),
                            new RecordAttributes(attributes)))))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .string(
                    containsString(
                        "It looks like you're trying to reference a record that does not exist.")));
  }

  @Test
  @Transactional
  public void expandColumnDefForNewData() throws Exception {
    String entityType = "to-alter";
    createSomeRecords(entityType, 1);
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("attr3", "convert this column from date to text");
    mockMvc
        .perform(
            put(
                    "/{instanceId}/records/{version}/{recordType}/{entityId}",
                    instanceId,
                    versionId,
                    entityType,
                    "entity_1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        new RecordRequest(
                            new RecordId("entity_0"),
                            new RecordType(entityType),
                            new RecordAttributes(attributes)))))
        .andExpect(status().isOk());
  }

  @Test
  @Transactional
  public void patchMissingEntity() throws Exception {
    String entityType = "to-patch";
    createSomeRecords(entityType, 1);
    Map<String, Object> entityAttributes = new HashMap<>();
    entityAttributes.put("attr-boolean", true);
    String entityId = "entity_missing";
    mockMvc
        .perform(
            patch(
                    "/{instanceId}/records/{version}/{recordType}/{entityId}",
                    instanceId,
                    versionId,
                    entityType,
                    entityId)
                .content(
                    mapper.writeValueAsString(
                        new RecordRequest(
                            new RecordId(entityId),
                            new RecordType(entityType),
                            new RecordAttributes(entityAttributes))))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @Transactional
  public void putEntityWithMissingTableReference() throws Exception {
    String entityType = "entity-type-missing-table-ref";
    String entityId = "entity_0";
    Map<String, Object> entityAttributes = new HashMap<>();
    String ref = RelationUtils.createRelationString("missing", "missing_also");
    entityAttributes.put("sample-ref", ref);

    mockMvc
        .perform(
            put(
                    "/{instanceId}/records/{version}/{recordType}/{entityId}",
                    instanceId,
                    versionId,
                    entityType,
                    entityId)
                .content(
                    mapper.writeValueAsString(
                        new RecordRequest(
                            new RecordId(entityId),
                            new RecordType(entityType),
                            new RecordAttributes(entityAttributes))))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(
            content().string(containsString("assign a reference to a table that does not exist")));
  }

  @Test
  @Transactional
  public void tryToAssignReferenceToNonRefColumn() throws Exception {
    String recordType = "ref-alter";
    createSomeRecords(recordType, 1);
    Map<String, Object> attributes = new HashMap<>();
    String ref = RelationUtils.createRelationString("missing", "missing_also");
    attributes.put("attr1", ref);
    mockMvc
        .perform(
            patch(
                    "/{instanceId}/records/{version}/{recordType}/{entityId}",
                    instanceId,
                    versionId,
                    recordType,
                    "record_0")
                .content(
                    mapper.writeValueAsString(
                        new RecordRequest(
                            new RecordId("record_0"),
                            new RecordType(recordType),
                            new RecordAttributes(attributes))))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(
            result ->
                assertTrue(
                    result
                        .getResolvedException()
                        .getMessage()
                        .contains(
                            "reference to an existing column that was not configured for references")));
  }

  private void createSomeRecords(String recordType, int numRecords) throws Exception {
    for (int i = 0; i < numRecords; i++) {
      String recordId = "record_" + i;
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("attr1", RandomStringUtils.randomAlphabetic(6));
      attributes.put("attr2", RandomUtils.nextFloat());
      attributes.put("attr3", "2022-11-01");
      attributes.put("attr4", RandomStringUtils.randomNumeric(5));
      attributes.put("attr5", RandomUtils.nextLong());
      attributes.put("attr-dt", "2022-03-01T12:00:03");
      attributes.put("attr-json", "{\"foo\":\"bar\"}");
      attributes.put("attr-boolean", true);
      mockMvc
          .perform(
              put(
                      "/{instanceId}/records/{version}/{recordType}/{entityId}",
                      instanceId,
                      versionId,
                      recordType,
                      recordId)
                  .content(
                      mapper.writeValueAsString(
                          new RecordRequest(
                              new RecordId(recordId),
                              new RecordType(recordType),
                              new RecordAttributes(attributes))))
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());
    }
  }
}

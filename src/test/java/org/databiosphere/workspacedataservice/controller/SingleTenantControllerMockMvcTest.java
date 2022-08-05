package org.databiosphere.workspacedataservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.databiosphere.workspacedataservice.TestUtils;
import org.databiosphere.workspacedataservice.shared.model.EntityUpsert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SingleTenantControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    private static UUID workspaceId;

    @BeforeAll
    private static void createWorkspace(){
        workspaceId = UUID.randomUUID();
    }

    @Test
    public void tryToDeleteMissingEntities() throws Exception {
        //missing entity type
        mockMvc.perform(post("/st/api/workspaces/{workspaceId}/entities/delete", workspaceId)
                .contentType(MediaType.APPLICATION_JSON).content("[{\"entityType\": \"samples\", \"entityName\": \"entity_1\"}]"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void tryFetchingMissingEntityType() throws Exception {
        mockMvc.perform(get("/st/api/workspaces/{workspaceId}/entityQuery/{entityType}", workspaceId, "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void tryFetchingMissingEntities() throws Exception {
        mockMvc.perform(get("/st/api/workspaces/{workspaceId}/entityQuery/{entityType}", workspaceId, "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void filterEntities() throws Exception {
        String entityType = "samples";
        createSomeEntities(entityType, 5);

        //ensure that we have all five entities present
        mockMvc.perform(get("/st/api/workspaces/{workspaceId}/entityQuery/{entityType}", workspaceId, entityType))
                .andExpect(content().string(containsString("\"filteredCount\":5")));

        //ensure that we can filter down to a single entity
        mockMvc.perform(get("/st/api/workspaces/{workspaceId}/entityQuery/{entityType}?filterTerms=entity_1", workspaceId, entityType))
                .andExpect(content().string(containsString("\"filteredCount\":1")));

        mockMvc.perform(get("/st/api/workspaces/{workspaceId}/entityQuery/{entityType}?filterTerms=entity_1", workspaceId, "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void addColumn() throws Exception {
        String entityType = "add-col";
        createSomeEntities(entityType, 1);

        mockMvc.perform(post("/st/api/workspaces/{workspaceId}/entities/batchUpsert", workspaceId)
                        .content("[{\"name\": \"entity_0\", \"entityType\": \"add-col\", \"operations\": [{\"op\": \"AddUpdateAttribute\", \"attributeName\": \"attr_new\", \"addUpdateAttribute\": \"text-attribute\"}]}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/st/api/workspaces/{workspaceId}/entityQuery/{entityType}", workspaceId, entityType))
                .andExpect(content().string(containsString("\"text-attribute\"")));

    }

    @Test
    public void addReferenceToExistingColumn() throws Exception {
        String entityType = "add-ref";
        createSomeEntities(entityType, 1);

        //try to supply new entity that has reference in attr1
        mockMvc.perform(post("/st/api/workspaces/{workspaceId}/entities/batchUpsert", workspaceId)
                        .content("[{\"name\": \"entity_0\", \"entityType\": \"add-ref\", \"operations\": [{\"op\": \"AddUpdateAttribute\", \"attributeName\": \"attr1\", \"addUpdateAttribute\": {\"entityType\": \"sample-references\", \"entityName\": \"entity_1\"}}]}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/st/api/workspaces/{workspaceId}/entities/batchUpsert", workspaceId)
                        .content("[{\"name\": \"entity_0\", \"entityType\": \"add-ref\", \"operations\": [{\"op\": \"AddUpdateAttribute\", \"attributeName\": \"brand-new\", \"addUpdateAttribute\": {\"entityType\": \"missing-entityType\", \"entityName\": \"entity_1\"}}]}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());

    }

    @Test
    public void deleteAttributes() throws Exception {
        String entityType = "delete-attrs";
        createSomeEntities(entityType, 1);

        //remove attr1
        mockMvc.perform(post("/st/api/workspaces/{workspaceId}/entities/batchUpsert", workspaceId)
                        .content("[{\"name\": \"entity_0\", \"entityType\": \"delete-attrs\", \"operations\": [{\"op\": \"RemoveAttribute\", \"attributeName\": \"attr2\"}]}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/st/api/workspaces/{workspaceId}/entityQuery/{entityType}", workspaceId, entityType))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"attr2\":null")));

    }

    @Test
    public void filterFields() throws Exception {
        String entityType = "fields";
        createSomeEntities(entityType, 3);

        mockMvc.perform(get("/st/api/workspaces/{workspaceId}/entityQuery/{entityType}", workspaceId, entityType))
                .andExpect(content().string(containsString("\"attr3\"")));

        mockMvc.perform(get("/st/api/workspaces/{workspaceId}/entityQuery/{entityType}?fields=attr1,attr2", workspaceId, entityType))
                .andExpect(content().string(not(containsString("\"attr3\""))));

    }

    @Test
    public void deleteEntities() throws Exception {
        createSomeEntities("to-delete", 2);
        mockMvc.perform(post("/st/api/workspaces/{workspaceId}/entities/delete", workspaceId)
                .contentType(MediaType.APPLICATION_JSON).content("[{\"entityType\": \"to-delete\", \"entityName\": \"entity_0\"}]")).andExpect(status().isNoContent());

        mockMvc.perform(post("/st/api/workspaces/{workspaceId}/entities/delete", workspaceId)
                .contentType(MediaType.APPLICATION_JSON).content("[{\"entityType\": \"to-delete\", \"entityName\": \"entity_0\"}]")).andExpect(status().isNotFound());
    }

    @Test
    public void testReferences() throws Exception {
        String entityType = "sample-references";
        createSomeEntities(entityType, 3);

        //add a new entity and entity type referencing the initial set of entities
        mockMvc.perform(post("/st/api/workspaces/{workspaceId}/entities/batchUpsert", workspaceId)
                .content("[{\"name\": \"entity_1\", \"entityType\": \"referring_entity_type\", \"operations\": [{\"op\": \"AddUpdateAttribute\", \"attributeName\": \"ref_col\", \"addUpdateAttribute\": {\"entityType\": \"sample-references\", \"entityName\": \"entity_1\"}}]}]")
                .contentType(MediaType.APPLICATION_JSON)).andDo(print());

        //try to delete referenced entity, get error
        mockMvc.perform(post("/st/api/workspaces/{workspaceId}/entities/delete", workspaceId)
                .contentType(MediaType.APPLICATION_JSON).content("[{\"entityType\": \"sample-references\", \"entityName\": \"entity_1\"}]")).andExpect(status().isBadRequest());

        //retrieve entity with reference attribute make sure it's present
        mockMvc.perform(get("/st/api/workspaces/{workspaceId}/entityQuery/{entityType}", workspaceId, "referring_entity_type"))
                .andExpect(content().string(containsString("{\"entityType\":\"sample-references\",\"entityName\":\"entity_1\"}")));
    }

    @Test
    public void createEntitiesWithNoAttrs() throws Exception {
        mockMvc.perform(post("/st/api/workspaces/{workspaceId}/entities/batchUpsert", workspaceId)
                        .content("[{\"name\": \"entity_1\", \"entityType\": \"no_atts\", \"operations\": []}]")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    private void createSomeEntities(String entityType, int numEntities) throws Exception {
        List<EntityUpsert> upserts = new ArrayList<>();
        for (int i = 0; i < numEntities; i++) {
            Map<String, Object> entityAttributes = new HashMap<>();
            entityAttributes.put("attr1", RandomStringUtils.randomAlphabetic(6));
            entityAttributes.put("attr2", RandomUtils.nextFloat());
            entityAttributes.put("attr3", "2022-11-01");
            entityAttributes.put("attr4", RandomStringUtils.randomNumeric(5));
            entityAttributes.put("attr5", RandomUtils.nextLong());
            entityAttributes.put("attr-dt", "2022-03-01T12:00:03");
            entityAttributes.put("attr-json", "{\"foo\":\"bar\"}");
            entityAttributes.put("attr-boolean", true);
            upserts.add(TestUtils.createEntityUpsert("entity_"+i, entityType, entityAttributes));
        }
        ObjectMapper mapper = new ObjectMapper();
        //create some entities to reference later
        mockMvc.perform(post("/st/api/workspaces/{workspaceId}/entities/batchUpsert", workspaceId)
                .content(mapper.writeValueAsString(upserts))
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNoContent());
    }
}

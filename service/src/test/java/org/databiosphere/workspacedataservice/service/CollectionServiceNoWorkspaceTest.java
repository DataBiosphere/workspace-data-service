package org.databiosphere.workspacedataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.databiosphere.workspacedataservice.shared.model.CollectionId;
import org.databiosphere.workspacedataservice.shared.model.WorkspaceId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@DirtiesContext
@SpringBootTest
@TestPropertySource(properties = {"twds.collection.workspace-id="})
class CollectionServiceNoWorkspaceTest {

  @Autowired private CollectionService collectionService;

  @Value("${twds.collection.workspace-id:}")
  private String workspaceIdProperty;

  @Test
  void assumptions() {
    // ensure the test is set up correctly, with an empty twds.collection.workspace-id property
    assertThat(workspaceIdProperty).isEmpty();
  }

  // when twds.collection.workspace-id is empty, collectionService.getWorkspaceId will echo the
  // collectionId back as the workspace id
  @Test
  void getWorkspaceId() {
    CollectionId collectionId = CollectionId.of(UUID.randomUUID());
    assertEquals(WorkspaceId.of(collectionId.id()), collectionService.getWorkspaceId(collectionId));
  }
}

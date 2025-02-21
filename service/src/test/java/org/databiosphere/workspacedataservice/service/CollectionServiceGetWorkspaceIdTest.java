package org.databiosphere.workspacedataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.databiosphere.workspacedataservice.workspace.WorkspaceDataTableType.RAWLS;
import static org.databiosphere.workspacedataservice.workspace.WorkspaceDataTableType.WDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.databiosphere.workspacedataservice.TestUtils;
import org.databiosphere.workspacedataservice.common.ControlPlaneTestBase;
import org.databiosphere.workspacedataservice.config.DataImportProperties;
import org.databiosphere.workspacedataservice.config.TenancyProperties;
import org.databiosphere.workspacedataservice.config.TwdsProperties;
import org.databiosphere.workspacedataservice.dao.WorkspaceRepository;
import org.databiosphere.workspacedataservice.dataimport.ImportValidator;
import org.databiosphere.workspacedataservice.rawls.RawlsClient;
import org.databiosphere.workspacedataservice.rawls.RawlsException;
import org.databiosphere.workspacedataservice.service.model.exception.CollectionException;
import org.databiosphere.workspacedataservice.service.model.exception.MissingObjectException;
import org.databiosphere.workspacedataservice.shared.model.CollectionId;
import org.databiosphere.workspacedataservice.shared.model.WorkspaceId;
import org.databiosphere.workspacedataservice.workspace.WorkspaceRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Tests for CollectionService.getWorkspaceId() */
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
class CollectionServiceGetWorkspaceIdTest extends ControlPlaneTestBase {

  @Autowired private CollectionService collectionService;

  @Autowired private NamedParameterJdbcTemplate namedTemplate;
  @Autowired private WorkspaceRepository workspaceRepository;

  @MockitoBean RawlsClient rawlsClient;
  @MockitoBean TenancyProperties tenancyProperties;
  @MockitoBean TwdsProperties twdsProperties;

  // mocks to satisfy bean dependencies, but unused
  @MockitoBean ImportValidator importValidator;
  @MockitoBean DataImportProperties dataImportProperties;

  @BeforeEach
  void beforeEach() {
    TestUtils.cleanAllCollections(collectionService, namedTemplate);
    TestUtils.cleanAllWorkspaces(namedTemplate);
  }

  @AfterAll
  void afterAll() {
    beforeEach(); // execute the same cleanup after all as we do before each
  }

  // single-tenant; workspace and collection both exist; workspace matches env var; collection
  // belongs to workspace
  @Test
  void singleTenant() {
    WorkspaceId workspaceId = WorkspaceId.of(UUID.randomUUID());

    when(twdsProperties.workspaceId()).thenReturn(workspaceId);
    when(tenancyProperties.getAllowVirtualCollections()).thenReturn(false);
    when(tenancyProperties.getEnforceCollectionsMatchWorkspaceId()).thenReturn(true);

    workspaceRepository.save(new WorkspaceRecord(workspaceId, WDS, true));
    CollectionId collectionId =
        CollectionId.of(collectionService.save(workspaceId, "name", "desc").getId());

    // expect getWorkspaceId to return the same workspace id that the dao returned
    assertEquals(workspaceId, collectionService.getWorkspaceId(collectionId));
  }

  // single-tenant; workspace and collection both exist; collection belongs to workspace -
  // but, workspaceId does not match env var
  @Test
  void singleTenantMismatchedWorkspaceId() {
    WorkspaceId workspaceId = WorkspaceId.of(UUID.randomUUID());

    // set a random env var that does not match
    when(twdsProperties.workspaceId()).thenReturn(WorkspaceId.of(UUID.randomUUID()));
    when(tenancyProperties.getAllowVirtualCollections()).thenReturn(false);
    when(tenancyProperties.getEnforceCollectionsMatchWorkspaceId()).thenReturn(true); // see below

    workspaceRepository.save(new WorkspaceRecord(workspaceId, WDS, true));

    // note: to save a collection into a workspace which does not match the env var, we need to
    // disable validation. However, we later want to assert the validation works. So, we turn off
    // validation, save, then turn it back on.
    when(tenancyProperties.getEnforceCollectionsMatchWorkspaceId()).thenReturn(false);
    CollectionId collectionId =
        CollectionId.of(collectionService.save(workspaceId, "name", "desc").getId());
    when(tenancyProperties.getEnforceCollectionsMatchWorkspaceId()).thenReturn(true);

    // expect getWorkspaceId to throw, since it found an unexpected workspace id
    assertThrows(CollectionException.class, () -> collectionService.getWorkspaceId(collectionId));
  }

  // single-tenant; collection does not exist
  @Test
  void singleTenantMissingWorkspaceId() {
    WorkspaceId workspaceId = WorkspaceId.of(UUID.randomUUID());

    when(twdsProperties.workspaceId()).thenReturn(workspaceId);
    when(tenancyProperties.getAllowVirtualCollections()).thenReturn(false);
    when(tenancyProperties.getEnforceCollectionsMatchWorkspaceId()).thenReturn(true);

    workspaceRepository.save(new WorkspaceRecord(workspaceId, WDS, true));
    // note: we do NOT insert a collection here; the collection doesn't exist

    // attempt to retrieve the workspaceid for the default collection, even though the default
    // collection doesn't exist
    CollectionId collectionId = CollectionId.of(workspaceId.id());
    MissingObjectException actual =
        assertThrows(
            MissingObjectException.class, () -> collectionService.getWorkspaceId(collectionId));

    // Assert
    assertThat(actual).hasMessageContaining("Collection does not exist");
  }

  // multi-tenant; workspace and collection both exist; no workspace env var; collection
  // belongs to workspace
  @Test
  void multiTenant() {
    WorkspaceId workspaceId = WorkspaceId.of(UUID.randomUUID());

    when(twdsProperties.workspaceId()).thenReturn(null);
    when(tenancyProperties.getAllowVirtualCollections()).thenReturn(true);
    when(tenancyProperties.getEnforceCollectionsMatchWorkspaceId()).thenReturn(false);

    workspaceRepository.save(new WorkspaceRecord(workspaceId, WDS, true));
    CollectionId collectionId =
        CollectionId.of(collectionService.save(workspaceId, "name", "desc").getId());

    // expect getWorkspaceId to return the same workspace id that the dao returned
    assertEquals(workspaceId, collectionService.getWorkspaceId(collectionId));
  }

  // multi-tenant; workspace exists but collection does not; workspace is RAWLS.
  // since virtual collections are allowed, returns workspaceId=collectionId
  @Test
  void multiTenantVirtualRawls() {
    WorkspaceId workspaceId = WorkspaceId.of(UUID.randomUUID());

    when(twdsProperties.workspaceId()).thenReturn(null);
    when(tenancyProperties.getAllowVirtualCollections()).thenReturn(true);
    when(tenancyProperties.getEnforceCollectionsMatchWorkspaceId()).thenReturn(false);

    workspaceRepository.save(new WorkspaceRecord(workspaceId, RAWLS, true));

    // request the collection whose id matches the known workspace
    assertEquals(workspaceId, collectionService.getWorkspaceId(CollectionId.of(workspaceId.id())));
  }

  // multi-tenant; workspace exists but collection does not; workspace is WDS.
  // even though virtual collections are allowed, throws error because workspace is WDS-powered.
  @Test
  void multiTenantVirtualWds() {
    WorkspaceId workspaceId = WorkspaceId.of(UUID.randomUUID());

    when(twdsProperties.workspaceId()).thenReturn(null);
    when(tenancyProperties.getAllowVirtualCollections()).thenReturn(true);
    when(tenancyProperties.getEnforceCollectionsMatchWorkspaceId()).thenReturn(false);

    workspaceRepository.save(new WorkspaceRecord(workspaceId, WDS, true));

    CollectionId virtualCollectionId = CollectionId.of(workspaceId.id());
    MissingObjectException actual =
        assertThrows(
            MissingObjectException.class,
            () -> collectionService.getWorkspaceId(virtualCollectionId));

    // Assert
    assertThat(actual).hasMessageContaining("Collection does not exist");
  }

  // multi-tenant; workspace virtual collections allowed; workspace does not exist
  @Test
  void multiTenantVirtualMissingWorkspace() {
    WorkspaceId workspaceId = WorkspaceId.of(UUID.randomUUID());

    when(twdsProperties.workspaceId()).thenReturn(null);
    when(tenancyProperties.getAllowVirtualCollections()).thenReturn(true);
    when(tenancyProperties.getEnforceCollectionsMatchWorkspaceId()).thenReturn(false);

    when(rawlsClient.getWorkspaceDetails(workspaceId.id()))
        .thenThrow(new RawlsException(HttpStatus.NOT_FOUND, "unit test intentional error"));

    // note we do not insert a workspace here

    CollectionId virtualCollectionId = CollectionId.of(workspaceId.id());
    MissingObjectException actual =
        assertThrows(
            MissingObjectException.class,
            () -> collectionService.getWorkspaceId(virtualCollectionId));

    // Assert
    assertThat(actual).hasMessageContaining("Collection does not exist");
  }

  // multi-tenant; workspace virtual collections allowed; could not contact Rawls to determine
  // the type of workspace.
  @Test
  void multiTenantVirtualErrorFromWorkspace() {
    WorkspaceId workspaceId = WorkspaceId.of(UUID.randomUUID());

    when(twdsProperties.workspaceId()).thenReturn(null);
    when(tenancyProperties.getAllowVirtualCollections()).thenReturn(true);
    when(tenancyProperties.getEnforceCollectionsMatchWorkspaceId()).thenReturn(false);

    when(rawlsClient.getWorkspaceDetails(workspaceId.id()))
        .thenThrow(new RawlsException(HttpStatus.BAD_GATEWAY, "failure contacting Rawls"));

    // note we do not insert a workspace here

    CollectionId virtualCollectionId = CollectionId.of(workspaceId.id());
    CollectionException actual =
        assertThrows(
            CollectionException.class, () -> collectionService.getWorkspaceId(virtualCollectionId));

    // Assert
    assertThat(actual).hasMessage("Unexpected error validating collection");
  }
}

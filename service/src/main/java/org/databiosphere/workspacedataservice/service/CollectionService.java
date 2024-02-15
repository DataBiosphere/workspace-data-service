package org.databiosphere.workspacedataservice.service;

import static org.databiosphere.workspacedataservice.service.RecordUtils.validateVersion;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.databiosphere.workspacedataservice.activitylog.ActivityLogger;
import org.databiosphere.workspacedataservice.config.TwdsProperties;
import org.databiosphere.workspacedataservice.dao.CollectionDao;
import org.databiosphere.workspacedataservice.sam.SamDao;
import org.databiosphere.workspacedataservice.service.model.exception.AuthorizationException;
import org.databiosphere.workspacedataservice.service.model.exception.CollectionException;
import org.databiosphere.workspacedataservice.service.model.exception.MissingObjectException;
import org.databiosphere.workspacedataservice.shared.model.CollectionId;
import org.databiosphere.workspacedataservice.shared.model.WorkspaceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CollectionService {

  private final CollectionDao collectionDao;
  private final SamDao samDao;
  private final ActivityLogger activityLogger;

  private final TwdsProperties twdsProperties;

  private WorkspaceId workspaceId;

  private static final Logger LOGGER = LoggerFactory.getLogger(CollectionService.class);

  public CollectionService(
      CollectionDao collectionDao,
      SamDao samDao,
      ActivityLogger activityLogger,
      TwdsProperties twdsProperties) {
    this.collectionDao = collectionDao;
    this.samDao = samDao;
    this.activityLogger = activityLogger;
    this.twdsProperties = twdsProperties;
    // stash the workspace id from config into a member var
    if (twdsProperties.getInstance() != null
        && twdsProperties.getInstance().getWorkspaceUuid() != null) {
      workspaceId = WorkspaceId.of(twdsProperties.getInstance().getWorkspaceUuid());
    }
  }

  public List<UUID> listCollections(String version) {
    validateVersion(version);
    return collectionDao.listCollectionSchemas();
  }

  // TODO update this javadoc, also are there logical problems with the sam resource type?
  // Also check the naming on these methods here
  /**
   * Creates a WDS collection, comprised of a Postgres schema and a Sam resource of type
   * "workspace". The Postgres schema will have an id of `collectionId`, which will match the Sam
   * resource's`workspaceId` unless otherwise specified.
   *
   * @param collectionId id of the collection to create
   * @param version WDS API version
   */
  public void createCollection(UUID collectionId, String version) {
    validateVersion(version);

    // check that the current user has permission on the parent workspace
    boolean hasCreateCollectionPermission = samDao.hasCreateCollectionPermission();
    LOGGER.debug("hasCreateCollectionPermission? {}", hasCreateCollectionPermission);

    if (!hasCreateCollectionPermission) {
      throw new AuthorizationException("Caller does not have permission to create collection.");
    }

    if (collectionDao.collectionSchemaExists(collectionId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This collection already exists");
    }

    // create collection schema in Postgres
    collectionDao.createSchema(collectionId);

    activityLogger.saveEventForCurrentUser(
        user -> user.created().collection().withUuid(collectionId));
  }

  public void deleteCollection(UUID collectionId, String version) {
    validateVersion(version);
    validateCollection(collectionId);

    // check that the current user has permission to delete the Sam resource
    boolean hasDeleteCollectionPermission = samDao.hasDeleteCollectionPermission();
    LOGGER.debug("hasDeleteCollectionPermission? {}", hasDeleteCollectionPermission);

    if (!hasDeleteCollectionPermission) {
      throw new AuthorizationException("Caller does not have permission to delete collection.");
    }

    // delete collection schema in Postgres
    collectionDao.dropSchema(collectionId);

    activityLogger.saveEventForCurrentUser(
        user -> user.deleted().collection().withUuid(collectionId));
  }

  public void validateCollection(UUID collectionId) {
    if (!collectionDao.collectionSchemaExists(collectionId)) {
      throw new MissingObjectException("Collection");
    }
  }

  public boolean canReadCollection(CollectionId collectionId) {
    return samDao.hasReadWorkspacePermission(getWorkspaceId(collectionId).toString());
  }

  public boolean canWriteCollection(CollectionId collectionId) {
    return samDao.hasWriteWorkspacePermission(getWorkspaceId(collectionId).toString());
  }

  /**
   * Return the workspace that contains the specified collection. The logic for this method is:
   *
   * <p>- look up the row for this collection in sys_wds.collection.
   *
   * <p>- if the WORKSPACE_ID env var is set and a row exists in sys_wds.collection, confirm the
   * row's workspace_id column matches the env var. If it does, return its value. If it doesn't
   * match, throw an error.
   *
   * <p>- else, return the CollectionId value as the WorkspaceId. This perpetuates the assumption
   * that collection and workspace ids are the same. But, this will be true in the GCP control plane
   * as long as Rawls continues to manage data tables, since "collection" is a WDS concept and does
   * not exist in Rawls.
   *
   * @param collectionId the collection for which to look up the workspace
   * @return the workspace containing the given collection.
   */
  public WorkspaceId getWorkspaceId(CollectionId collectionId) {
    // look up the workspaceId for this collection in the collection table
    WorkspaceId rowWorkspaceId = null;
    try {
      rowWorkspaceId = collectionDao.getWorkspaceId(collectionId);
      // as of this writing, if a deployment allows virtual collections, it is an error if the
      // collection DOES exist.
      if (twdsProperties.getTenancy() != null
          && twdsProperties.getTenancy().getAllowVirtualCollections()) {
        throw new CollectionException("Expected a virtual collection");
      }
    } catch (EmptyResultDataAccessException e) {
      // does this deployment allow for virtual collections?
      // TODO AJ-1630: enable this error for data-plane WDS, which does not allow virtual
      // collections
      /*
      if (twdsProperties.getTenancy() == null
          || !twdsProperties.getTenancy().getAllowVirtualCollections()) {
        throw new MissingObjectException("Collection");
      }
      */
    }

    // safety check: if we found a workspace id in the db, it indicates we are in a data-plane
    // single-tenant WDS. Verify the workspace matches the $WORKSPACE_ID env var.
    // we must remove this check in a future multi-tenant WDS.
    if (rowWorkspaceId != null && !rowWorkspaceId.equals(workspaceId)) {
      // log the details, including expected/actual workspace ids
      LOGGER.error(
          "Found unexpected workspaceId for collection {}. Expected {}, got {}.",
          collectionId,
          workspaceId,
          rowWorkspaceId);
      // but, don't include workspace ids when throwing a user-facing error
      throw new CollectionException(
          "Found unexpected workspaceId for collection %s.".formatted(collectionId));
    }

    return Objects.requireNonNullElseGet(rowWorkspaceId, () -> WorkspaceId.of(collectionId.id()));
  }
}

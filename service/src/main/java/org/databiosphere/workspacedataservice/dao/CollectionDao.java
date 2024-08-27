package org.databiosphere.workspacedataservice.dao;

import org.databiosphere.workspacedataservice.shared.model.CollectionId;
import org.databiosphere.workspacedataservice.shared.model.WorkspaceId;

@Deprecated(forRemoval = true, since = "v0.18.0")
public interface CollectionDao {
  // 18 usages
  boolean collectionSchemaExists(CollectionId collectionId);

  // 1 usage
  void alterSchema(CollectionId oldCollectionId, CollectionId newCollectionId);

  // 10 usages
  WorkspaceId getWorkspaceId(CollectionId collectionId);
}

package org.databiosphere.workspacedataservice.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

public class GcsStorageTest {

  private String bucketName = "test-bucket";

  private String projectId = "test-projectId";

  private final Storage mockStorage = LocalStorageHelper.getOptions().getService();

  @Test
  public void testCreateandGetBlobSimple() {
    GcsStorage storage = new GcsStorage(mockStorage, bucketName, projectId);
    String initialString = "text";
    InputStream targetStream = new ByteArrayInputStream(initialString.getBytes());
    BlobId blobId = storage.createGcsFile(targetStream);

    assertEquals(bucketName, blobId.getBucket());

    String contents = storage.getBlobContents(blobId.getName());
    assertThat(contents).isEqualTo(initialString);
  }
}

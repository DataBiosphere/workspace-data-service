package org.databiosphere.workspacedataservice.storage;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.specialized.BlobOutputStream;
import org.databiosphere.workspacedataservice.service.model.exception.LaunchProcessException;
import org.databiosphere.workspacedataservice.workspacemanager.WorkspaceManagerDao;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class AzureBlobStorage implements BackUpFileStorage {
    private final WorkspaceManagerDao workspaceManagerDao;
    private static String backUpContainerName = "backup";
    public AzureBlobStorage(WorkspaceManagerDao workspaceManagerDao) {
        this.workspaceManagerDao = workspaceManagerDao;
    }

    @Override
    public void streamOutputToBlobStorage(InputStream fromStream, String blobName) {
        // TODO: remove this once connection is switched to be done via SAS token
        String storageConnectionString = System.getenv("STORAGE_CONNECTION_STRING");
        BlobContainerClient blobContainerClient = constructBlockBlobClient(backUpContainerName, storageConnectionString);

        // https://learn.microsoft.com/en-us/java/api/overview/azure/storage-blob-readme?view=azure-java-stable#upload-a-blob-via-an-outputstream
        try (BlobOutputStream blobOS = blobContainerClient.getBlobClient(blobName).getBlockBlobClient().getBlobOutputStream()) {
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fromStream, StandardCharsets.UTF_8))) {
                int line;
                while ((line = bufferedReader.read()) != -1) {
                    blobOS.write(line);
                }
            }
        } catch (IOException ioEx) {
            throw new LaunchProcessException("Error streaming output of child process", ioEx);
        }
    }

    public BlobContainerClient constructBlockBlobClient(String containerName, String connectionString) {
        // get  workspace blob storage endpoint and token
        var blobstorageDetails = workspaceManagerDao.getBlobStorageUrl();

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().endpoint(blobstorageDetails.getUrl()).sasToken(blobstorageDetails.getToken()).buildClient();

        // if the backup container in storage doesnt already exists, it will need to be created
        try {
            return blobServiceClient.getBlobContainerClient(containerName);
        }
        catch (BlobStorageException e){
            return blobServiceClient.createBlobContainerIfNotExists(containerName);
        }
    }
}

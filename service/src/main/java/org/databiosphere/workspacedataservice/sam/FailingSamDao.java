package org.databiosphere.workspacedataservice.sam;

import org.broadinstitute.dsde.workbench.client.sam.model.SystemStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will be used instead of HttpSamDao if the WORKSPACE_ID env var
 * contains a non-UUID value, including being blank.
 * <p>
 * This class will fail all permission checks and log a warning each time. Its
 * objective is to disallow any write operations that require permissions, while still
 * allowing WDS to start up and for users to read any data already in WDS.
 */
public class FailingSamDao implements SamDao {

    private final String errorMessage;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public FailingSamDao(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public boolean hasCreateInstancePermission() {
        logWarning();
        return false;
    }

    @Override
    public boolean hasCreateInstancePermission(String token) {
        logWarning();
        return false;
    }

    @Override
    public boolean hasDeleteInstancePermission() {
        logWarning();
        return false;
    }

    @Override
    public boolean hasDeleteInstancePermission(String token) {
        logWarning();
        return false;
    }

    @Override
    public boolean hasWriteInstancePermission() {
        logWarning();
        return false;
    }

    @Override
    public boolean hasWriteInstancePermission(String token) {
        logWarning();
        return false;
    }

    @Override
    public SystemStatus getSystemStatus() {
        throw new RuntimeException("Sam integration failure: " + errorMessage);
    }

    private void logWarning() {
        logger.warn("Sam permission check failed. {}", errorMessage);
    }


}

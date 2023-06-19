package org.databiosphere.workspacedataservice;

import org.apache.commons.lang3.StringUtils;
import org.databiosphere.workspacedataservice.dao.BackupDao;
import org.databiosphere.workspacedataservice.dao.InstanceDao;
import org.databiosphere.workspacedataservice.leonardo.HttpLeonardoClientFactory;
import org.databiosphere.workspacedataservice.leonardo.LeonardoClientFactory;
import org.databiosphere.workspacedataservice.leonardo.LeonardoDao;
import org.databiosphere.workspacedataservice.service.model.BackupSchema;
import org.databiosphere.workspacedataservice.sourcewds.HttpWorkspaceDataServiceClientFactory;
import org.databiosphere.workspacedataservice.sourcewds.WorkspaceDataServiceClientFactory;
import org.databiosphere.workspacedataservice.sourcewds.WorkspaceDataServiceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;

import java.util.UUID;

public class InstanceInitializerBean {

    private final InstanceDao instanceDao;
    private final BackupDao backupDao;

    @Value("${twds.instance.workspace-id}")
    private String workspaceId;

    @Value("${twds.instance.source-workspace-id}")
    private String sourceWorkspaceId;

    /*
        currently unused; future code will use this token to:
            - ask WSM about the workspace's storage container
            - retrieve a SAS token for that container from WSM
            - kick off a backup operation in the source WDS
     */
    @Value("${twds.startup-token}")
    private String startupToken;

    @Value("${leoUrl}")
    private String leoUrl;

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceInitializerBean.class);

    public InstanceInitializerBean(InstanceDao instanceDao, BackupDao backupDao){
        this.instanceDao = instanceDao;
        this.backupDao = backupDao;
    }

    public boolean isInCloneMode(String sourceWorkspaceId) {
        if (StringUtils.isNotBlank(sourceWorkspaceId)){
            LOGGER.info("Source workspace id found, checking database");
            try {
                UUID.fromString(sourceWorkspaceId);
            } catch (IllegalArgumentException e){
                    LOGGER.warn("Source workspace id could not be parsed, unable to clone DB. Provided source workspace id: {}.", sourceWorkspaceId);
                    return false;
            }
            try {
                // TODO at this stage of cloning work (where only backup is getting generated), just checking if an instance schema already exists is sufficient
                // when the restore operation is added, it would be important to check if any record of restore state is present
                // it is also possible to check if backup was initiated and completed (since if it did, we dont need to request it again)
                // and can just kick off the restore
                return !instanceDao.instanceSchemaExists(UUID.fromString(workspaceId));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Workspace id could not be parsed, unable to clone DB. Provided default workspace id: {}.", workspaceId);
                return false;
            }
        }
        LOGGER.info("No source workspace id found, initializing default schema.");
        return false;

    }

    public void initCloneMode(){
        LOGGER.info("Starting in clone mode...");
        LOGGER.info("start up token for debug: " + startupToken);

        try {
            // first get source wds url based on source workspace id and the provided access token
            LeonardoClientFactory leofactory = new HttpLeonardoClientFactory(leoUrl);
            LeonardoDao leoDao = new LeonardoDao(leofactory, sourceWorkspaceId);
            var sourceWdsEndpoint = leoDao.getWdsEndpointUrl(startupToken);

            // make the call to source wds to trigger back up
            WorkspaceDataServiceClientFactory wdsfactory = new HttpWorkspaceDataServiceClientFactory(sourceWdsEndpoint);
            WorkspaceDataServiceDao wdsDao = new WorkspaceDataServiceDao(wdsfactory, sourceWorkspaceId);

            // check if our current workspace has already sent a request for backup for the source
            // if it did, no need to do it again
            if (!backupDao.backupExists(UUID.fromString(sourceWorkspaceId))) {
                // TODO since the backup api is not async, this will return once the backup finishes
                var response = wdsDao.triggerBackup(startupToken, UUID.fromString(workspaceId));

                // in current WDS backup state, record that backup was kicked off (save the source workspace id)
                backupDao.createBackupEntry(response.getTrackingId(), UUID.fromString(sourceWorkspaceId));

                // check that backup was successfully kicked off, next wait to check if backup is ready
                var complete = false;
                var backupFileName = "";

                long startTime = System.currentTimeMillis(); //fetch starting time
                while (!complete || (System.currentTimeMillis()-startTime)<3600000) { // exit loop after 60 minutes
                    LOGGER.info("Checking status for tracking id " + response.getTrackingId());
                    var statusResponse = wdsDao.checkBackupStatus(startupToken, response.getTrackingId());
                    if (statusResponse.getState() == BackupSchema.BackupState.Completed.toString() || statusResponse.getState() == BackupSchema.BackupState.Error.toString()) {
                        backupDao.updateBackupStatus(response.getTrackingId(),statusResponse.getState());
                        backupFileName = statusResponse.getFilename();
                        complete = true;
                    }
                    // sleep 10 seconds before pulling state again
                    Thread.sleep(10 * 1000);
                }

                if(!complete) {
                    LOGGER.error("An error occured during clone mode.");
                }
            }

            //TODO do the restore
        }
        catch(Exception e){
            LOGGER.error("An error occured during clone mode.");
        }
    }

    public void initializeInstance() {
        LOGGER.info("Default workspace id loaded as {}.", workspaceId);
        if (isInCloneMode(sourceWorkspaceId)) {
            LOGGER.info("Source workspace id loaded as {}.", sourceWorkspaceId);
            initCloneMode();
        }

        //TODO Wrap this in an else once restore for cloning is implemented (currently only backup is being kicked off)
        initializeDefaultInstance();
    }

    public void initializeDefaultInstance() {

        try {
            UUID instanceId = UUID.fromString(workspaceId);

            if (!instanceDao.instanceSchemaExists(instanceId)) {
                instanceDao.createSchema(instanceId);
                LOGGER.info("Creating default schema id succeeded for workspaceId {}.", workspaceId);
            } else {
                LOGGER.debug("Default schema for workspaceId {} already exists; skipping creation.", workspaceId);
            }

        } catch (IllegalArgumentException e) {
            LOGGER.warn("Workspace id could not be parsed, a default schema won't be created. Provided id: {}.", workspaceId);
        } catch (DataAccessException e) {
            LOGGER.error("Failed to create default schema id for workspaceId {}.", workspaceId);
        }
    }

}

package org.databiosphere.workspacedataservice;

import org.databiosphere.workspacedataservice.dao.BackupDao;
import org.databiosphere.workspacedataservice.dao.CloneDao;
import org.databiosphere.workspacedataservice.dao.InstanceDao;
import org.databiosphere.workspacedataservice.leonardo.LeonardoDao;
import org.databiosphere.workspacedataservice.service.BackupRestoreService;
import org.databiosphere.workspacedataservice.sourcewds.WorkspaceDataServiceDao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InstanceInitializerConfig {

    @Bean
    public InstanceInitializerBean instanceInitializerBean(InstanceDao instanceDao, BackupDao backupDao, LeonardoDao leoDao, WorkspaceDataServiceDao wdsDao, CloneDao cloneDao, BackupRestoreService restoreService) {
        return new InstanceInitializerBean(instanceDao, backupDao, leoDao, wdsDao, cloneDao, restoreService);
    }
}

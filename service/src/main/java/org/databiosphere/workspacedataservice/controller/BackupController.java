package org.databiosphere.workspacedataservice.controller;

import org.databiosphere.workspacedataservice.retry.RetryableApi;
import org.databiosphere.workspacedataservice.service.BackupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    // TODO: We may not need the workspaceId
    @PostMapping("/backup/azure/{instanceId}")
    public ResponseEntity<String> createBackup(@PathVariable("instanceId") UUID instanceId, @RequestParam(name= "workspaceId") UUID workspaceId) {
        backupService.backupAzureWDS(instanceId, workspaceId);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
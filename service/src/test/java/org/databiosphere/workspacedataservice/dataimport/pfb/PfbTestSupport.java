package org.databiosphere.workspacedataservice.dataimport.pfb;

import static org.databiosphere.workspacedataservice.dataimport.pfb.PfbTestUtils.stubJobContext;

import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.util.UUID;
import org.databiosphere.workspacedataservice.activitylog.ActivityLogger;
import org.databiosphere.workspacedataservice.dao.JobDao;
import org.databiosphere.workspacedataservice.generated.GenericJobServerModel;
import org.databiosphere.workspacedataservice.generated.ImportRequestServerModel;
import org.databiosphere.workspacedataservice.recordstream.RecordSourceFactory;
import org.databiosphere.workspacedataservice.retry.RestClientRetry;
import org.databiosphere.workspacedataservice.service.BatchWriteService;
import org.databiosphere.workspacedataservice.service.ImportService;
import org.databiosphere.workspacedataservice.workspacemanager.WorkspaceManagerDao;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
class PfbTestSupport {
  @Autowired private JobDao jobDao;
  @Autowired private RestClientRetry restClientRetry;
  @Autowired private RecordSourceFactory recordSourceFactory;
  @Autowired private BatchWriteService batchWriteService;
  @Autowired private ActivityLogger activityLogger;
  @Autowired private ObservationRegistry observationRegistry;
  @Autowired private ImportService importService;
  @Autowired private WorkspaceManagerDao wsmDao;

  void executePfbImportQuartzJob(UUID collectionId, Resource pfbResource)
      throws IOException, JobExecutionException {
    ImportRequestServerModel importRequest =
        new ImportRequestServerModel(ImportRequestServerModel.TypeEnum.PFB, pfbResource.getURI());

    // because we have a mock scheduler dao, this won't trigger Quartz
    GenericJobServerModel genericJobServerModel =
        importService.createImport(collectionId, importRequest);

    UUID jobId = genericJobServerModel.getJobId();
    JobExecutionContext mockContext = stubJobContext(jobId, pfbResource, collectionId);

    buildPfbQuartzJob(collectionId).execute(mockContext);
  }

  PfbQuartzJob buildPfbQuartzJob() {
    return buildPfbQuartzJob(UUID.randomUUID());
  }

  private PfbQuartzJob buildPfbQuartzJob(UUID workspaceId) {
    return new PfbQuartzJob(
        jobDao,
        wsmDao,
        restClientRetry,
        recordSourceFactory,
        batchWriteService,
        activityLogger,
        observationRegistry,
        workspaceId);
  }
}

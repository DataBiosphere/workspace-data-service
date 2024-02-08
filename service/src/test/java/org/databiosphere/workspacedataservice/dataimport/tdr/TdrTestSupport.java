package org.databiosphere.workspacedataservice.dataimport.tdr;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import java.net.URL;
import java.util.UUID;
import org.databiosphere.workspacedataservice.activitylog.ActivityLogger;
import org.databiosphere.workspacedataservice.dao.JobDao;
import org.databiosphere.workspacedataservice.recordstream.StreamingWriteHandlerFactory;
import org.databiosphere.workspacedataservice.retry.RestClientRetry;
import org.databiosphere.workspacedataservice.service.BatchWriteService;
import org.databiosphere.workspacedataservice.workspacemanager.WorkspaceManagerDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class TdrTestSupport {
  @Autowired private JobDao jobDao;
  @Autowired private WorkspaceManagerDao wsmDao;
  @Autowired private RestClientRetry restClientRetry;
  @Autowired private StreamingWriteHandlerFactory streamingWriteHandlerFactory;
  @Autowired private BatchWriteService batchWriteService;
  @Autowired private ActivityLogger activityLogger;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ObservationRegistry observationRegistry;

  /** Returns a TdrManifestQuartzJob that is capable of pulling parquet files from the classpath. */
  TdrManifestQuartzJob buildTdrManifestQuartzJob(UUID workspaceId) {
    return new TdrManifestQuartzJob(
        jobDao,
        wsmDao,
        restClientRetry,
        streamingWriteHandlerFactory,
        batchWriteService,
        activityLogger,
        workspaceId,
        objectMapper,
        observationRegistry) {
      @Override
      protected URL parseUrl(String path) {
        if (path.startsWith("classpath:")) {
          return getClass().getClassLoader().getResource(path.substring("classpath:".length()));
        }
        return super.parseUrl(path);
      }
    };
  }
}

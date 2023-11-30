package org.databiosphere.workspacedataservice.dataimport;

import static org.databiosphere.workspacedataservice.shared.model.Schedulable.ARG_INSTANCE;
import static org.databiosphere.workspacedataservice.shared.model.Schedulable.ARG_TOKEN;
import static org.databiosphere.workspacedataservice.shared.model.Schedulable.ARG_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.workspace.model.ResourceList;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.databiosphere.workspacedataservice.activitylog.ActivityLogger;
import org.databiosphere.workspacedataservice.dao.JobDao;
import org.databiosphere.workspacedataservice.dao.SchedulerDao;
import org.databiosphere.workspacedataservice.generated.GenericJobServerModel;
import org.databiosphere.workspacedataservice.generated.ImportRequestServerModel;
import org.databiosphere.workspacedataservice.retry.RestClientRetry;
import org.databiosphere.workspacedataservice.service.BatchWriteService;
import org.databiosphere.workspacedataservice.service.ImportService;
import org.databiosphere.workspacedataservice.service.InstanceService;
import org.databiosphere.workspacedataservice.service.RecordOrchestratorService;
import org.databiosphere.workspacedataservice.service.model.RecordTypeSchema;
import org.databiosphere.workspacedataservice.workspacemanager.WorkspaceManagerDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.impl.JobDetailImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.Resource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles(profiles = "mock-sam")
@DirtiesContext
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PfbQuartzJobE2ETest {

  @Autowired JobDao jobDao;
  @Autowired RestClientRetry restClientRetry;
  @Autowired BatchWriteService batchWriteService;
  @Autowired ActivityLogger activityLogger;
  @Autowired RecordOrchestratorService recordOrchestratorService;
  @Autowired ImportService importService;
  @Autowired InstanceService instanceService;

  @MockBean SchedulerDao schedulerDao;
  @MockBean WorkspaceManagerDao wsmDao;

  // test resources used below
  @Value("classpath:four_tables.avro")
  Resource fourTablesAvroResource;

  @Value("classpath:test.avro")
  Resource testAvroResource;

  UUID instanceId;

  @BeforeAll
  void beforeAll() {
    doNothing().when(schedulerDao).schedule(any());
  }

  @BeforeEach
  void beforeEach() {
    instanceId = UUID.randomUUID();
    instanceService.createInstance(instanceId, "v0.2");
  }

  @AfterEach
  void afterEach() {
    instanceService.deleteInstance(instanceId, "v0.2");
  }

  @Test
  void importTestResource() throws IOException, JobExecutionException {
    ImportRequestServerModel importRequest =
        new ImportRequestServerModel(
            ImportRequestServerModel.TypeEnum.PFB, testAvroResource.getURI());

    // because we have a mock scheduler dao, this won't trigger Quartz
    GenericJobServerModel genericJobServerModel =
        importService.createImport(instanceId, importRequest);

    UUID jobId = genericJobServerModel.getJobId();
    JobExecutionContext mockContext = stubJobContext(jobId, testAvroResource, instanceId);

    // WSM should report no snapshots already linked to this workspace
    when(wsmDao.enumerateDataRepoSnapshotReferences(any(), anyInt(), anyInt()))
        .thenReturn(new ResourceList());

    buildQuartzJob().execute(mockContext);

    /* the testAvroResource should insert:
       - 3202 record(s) of type activities
       - 3202 record(s) of type files
       - 3202 record(s) of type donors
       - 3202 record(s) of type biosamples
       - 1 record(s) of type datasets
    */

    List<RecordTypeSchema> allTypes =
        recordOrchestratorService.describeAllRecordTypes(instanceId, "v0.2");

    // TODO: could assert on individual column data types to see if they are good

    Map<String, Integer> actualCounts =
        allTypes.stream()
            .collect(
                Collectors.toMap(
                    recordTypeSchema -> recordTypeSchema.name().getName(),
                    RecordTypeSchema::count));

    assertEquals(
        Map.of(
            "activities", 3202, "files", 3202, "donors", 3202, "biosamples", 3202, "datasets", 1),
        actualCounts);
  }

  @Test
  void importFourTablesResource() throws IOException, JobExecutionException {
    ImportRequestServerModel importRequest =
        new ImportRequestServerModel(
            ImportRequestServerModel.TypeEnum.PFB, fourTablesAvroResource.getURI());

    // because we have a mock scheduler dao, this won't trigger Quartz
    GenericJobServerModel genericJobServerModel =
        importService.createImport(instanceId, importRequest);

    UUID jobId = genericJobServerModel.getJobId();
    JobExecutionContext mockContext = stubJobContext(jobId, fourTablesAvroResource, instanceId);

    // WSM should report no snapshots already linked to this workspace
    when(wsmDao.enumerateDataRepoSnapshotReferences(any(), anyInt(), anyInt()))
        .thenReturn(new ResourceList());

    buildQuartzJob().execute(mockContext);

    /* the fourTablesAvroResource should insert:
       - 3 record(s) of type data_release
       - 1 record(s) of type submitted_aligned_reads
    */

    List<RecordTypeSchema> allTypes =
        recordOrchestratorService.describeAllRecordTypes(instanceId, "v0.2");

    Map<String, Integer> actualCounts =
        allTypes.stream()
            .collect(
                Collectors.toMap(
                    recordTypeSchema -> recordTypeSchema.name().getName(),
                    RecordTypeSchema::count));

    assertEquals(Map.of("data_release", 3, "submitted_aligned_reads", 1), actualCounts);
  }

  private JobExecutionContext stubJobContext(UUID jobId, Resource resource, UUID instanceId)
      throws IOException {
    JobExecutionContext mockContext = mock(JobExecutionContext.class);
    when(mockContext.getMergedJobDataMap())
        .thenReturn(
            new JobDataMap(
                Map.of(
                    ARG_TOKEN,
                    "expectedToken",
                    ARG_URL,
                    resource.getURL().toString(),
                    ARG_INSTANCE,
                    instanceId.toString())));

    JobDetailImpl jobDetail = new JobDetailImpl();
    jobDetail.setKey(new JobKey(jobId.toString(), "bar"));
    when(mockContext.getJobDetail()).thenReturn(jobDetail);

    return mockContext;
  }

  private PfbQuartzJob buildQuartzJob() {
    return new PfbQuartzJob(
        jobDao, wsmDao, restClientRetry, batchWriteService, activityLogger, UUID.randomUUID());
  }
}

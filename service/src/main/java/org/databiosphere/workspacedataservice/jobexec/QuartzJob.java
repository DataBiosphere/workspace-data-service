package org.databiosphere.workspacedataservice.jobexec;

import static org.databiosphere.workspacedataservice.generated.GenericJobServerModel.StatusEnum;
import static org.databiosphere.workspacedataservice.sam.BearerTokenFilter.ATTRIBUTE_NAME_TOKEN;
import static org.databiosphere.workspacedataservice.shared.model.Schedulable.ARG_TOKEN;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.net.URL;
import java.util.UUID;
import org.databiosphere.workspacedataservice.config.DataImportProperties;
import org.databiosphere.workspacedataservice.dao.JobDao;
import org.databiosphere.workspacedataservice.service.MDCServletRequestListener;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.MDC;

/**
 * WDS's base class for asynchronous Quartz jobs. Contains convenience methods and an overridable
 * best-practice implementation of `execute()`. This implementation:
 *
 * <p>- retrieves the job id as a UUID (Quartz stores it as a String)
 *
 * <p>- sets the WDS job to RUNNING
 *
 * <p>- calls the implementing class's `executeInternal()` method
 *
 * <p>- sets the WDS job to SUCCEEDED once `executeInternal()` finishes
 *
 * <p>- sets the WDS job to FAILED on any Exception from `executeInternal()`
 *
 * <p>Note this implements Quartz's `Job` interface, not WDS's own `Job` model.
 */
// note this implements Quartz's `Job`, not WDS's own `Job`
public abstract class QuartzJob implements Job {

  private final ObservationRegistry observationRegistry;
  protected final DataImportProperties dataImportProperties;

  protected QuartzJob(
      ObservationRegistry observationRegistry, DataImportProperties dataImportProperties) {
    this.observationRegistry = observationRegistry;
    this.dataImportProperties = dataImportProperties;
  }

  /** implementing classes are expected to be beans that inject a JobDao */
  protected abstract JobDao getJobDao();

  @Override
  public void execute(JobExecutionContext context) throws org.quartz.JobExecutionException {
    // retrieve jobId
    UUID jobId = UUID.fromString(context.getJobDetail().getKey().getName());

    // (try to) set the MDC request id based on the originating thread
    propagateMdc(context);

    Observation observation =
        Observation.start("wds.job.execute", observationRegistry)
            .contextualName("job-execution")
            .lowCardinalityKeyValue("jobType", getClass().getSimpleName())
            .highCardinalityKeyValue("jobId", jobId.toString());
    try {
      // mark this job as running
      getJobDao().running(jobId);
      observation.event(Observation.Event.of("job.running"));
      // look for an auth token in the Quartz JobDataMap
      String authToken = getJobDataString(context.getMergedJobDataMap(), ARG_TOKEN);
      // and stash the auth token into job context
      if (authToken != null) {
        JobContextHolder.init();
        JobContextHolder.setAttribute(ATTRIBUTE_NAME_TOKEN, authToken);
      }
      // execute the specifics of this job
      executeInternal(jobId, context);

      // if we reached here, and config says we should, mark this job as successful
      if (dataImportProperties.isSucceedOnCompletion()) {
        getJobDao().succeeded(jobId);
        observation.lowCardinalityKeyValue("outcome", StatusEnum.SUCCEEDED.getValue());
      } else {
        // ensure we give the observation an outcome, even though we left the job running
        observation.lowCardinalityKeyValue("outcome", StatusEnum.RUNNING.getValue());
      }
    } catch (Exception e) {
      // on any otherwise-unhandled exception, mark the job as failed
      getJobDao().fail(jobId, e);
      observation.error(e);
      observation.lowCardinalityKeyValue("outcome", StatusEnum.ERROR.getValue());
    } finally {
      JobContextHolder.destroy();
      observation.stop();
    }
  }

  protected abstract void executeInternal(UUID jobId, JobExecutionContext context);

  // try to retrieve MDC id from job context and add to this thread; don't fail if this errors out
  private void propagateMdc(JobExecutionContext context) {
    try {
      String requestId =
          getJobDataString(context.getMergedJobDataMap(), MDCServletRequestListener.MDC_KEY);
      MDC.put(MDCServletRequestListener.MDC_KEY, requestId);
    } catch (Exception e) {
      // noop
    }
  }

  /**
   * Retrieve a String value from a JobDataMap. Throws a JobExecutionException if the value is not
   * found/null or not a String.
   *
   * @param jobDataMap the map from which to retrieve the String
   * @param key where to find the String in the map
   * @return value from the JobDataMap
   */
  protected String getJobDataString(JobDataMap jobDataMap, String key) {
    String returnValue;
    try {
      returnValue = jobDataMap.getString(key);
      if (returnValue == null) {
        throw new JobExecutionException("Key '%s' was null in JobDataMap".formatted(key));
      }
      return returnValue;
    } catch (Exception e) {
      throw new JobExecutionException(
          "Error retrieving key %s from JobDataMap: %s".formatted(key, e.getMessage()), e);
    }
  }

  /**
   * Retrieve a UUID value from a JobDataMap. Throws a JobExecutionException if the value is not
   * found/null or not a UUID.
   *
   * @param jobDataMap the map from which to retrieve the UUID
   * @param key where to find the UUID in the map
   * @return value from the JobDataMap
   */
  protected UUID getJobDataUUID(JobDataMap jobDataMap, String key) {
    try {
      return UUID.fromString(jobDataMap.getString(key));
    } catch (Exception e) {
      throw new JobExecutionException(
          "Error retrieving key %s as UUID from JobDataMap: %s".formatted(key, e.getMessage()), e);
    }
  }

  /**
   * Retrieve a URL value from a JobDataMap. Throws a JobExecutionException if the value is not
   * found/null or cannot be parsed into a URL.
   *
   * @param jobDataMap the map from which to retrieve the URL
   * @param key where to find the URL in the map
   * @return value from the JobDataMap
   */
  protected URL getJobDataUrl(JobDataMap jobDataMap, String key) {
    try {
      return new URL(jobDataMap.getString(key));
    } catch (Exception e) {
      throw new JobExecutionException(
          "Error retrieving key %s as URL from JobDataMap: %s".formatted(key, e.getMessage()), e);
    }
  }
}

package org.databiosphere.workspacedataservice.config;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.google.common.collect.Sets;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/** Properties that dictate how data import processes should behave. */
public class DataImportProperties {
  private RecordSinkMode batchWriteRecordSink;
  private String projectId;
  private String rawlsBucketName;
  private boolean succeedOnCompletion;
  private final Set<AllowedImportSource> defaultAllowedImportSources =
      Set.of(
          new AllowedImportSource("storage.googleapis.com"),
          new AllowedImportSource("*.core.windows.net"),
          // S3 allows multiple URL formats
          // https://docs.aws.amazon.com/AmazonS3/latest/userguide/VirtualHosting.html
          new AllowedImportSource("s3.amazonaws.com"), // path style legacy global endpoint
          new AllowedImportSource("*.s3.amazonaws.com") // virtual host style legacy global endpoint
          );
  private Set<AllowedImportSource> allowedImportSources = Collections.emptySet();

  /** Where to write records after import, options are defined by {@link RecordSinkMode} */
  public RecordSinkMode getBatchWriteRecordSink() {
    return batchWriteRecordSink;
  }

  void setBatchWriteRecordSink(String batchWriteRecordSink) {
    this.batchWriteRecordSink = RecordSinkMode.fromValue(batchWriteRecordSink);
  }

  public String getProjectId() {
    return projectId;
  }

  void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getRawlsBucketName() {
    return rawlsBucketName;
  }

  void setRawlsBucketName(String rawlsBucketName) {
    this.rawlsBucketName = rawlsBucketName;
  }

  /**
   * Should Quartz-based jobs transition to SUCCEEDED when they complete internally in WDS? In the
   * control plane, where a logical "job" requires Rawls to receive and write data, the Quartz job
   * will complete well before Rawls writes data, so we should not mark the job as completed. Rawls
   * will send a message indicating when the logical job is complete.
   *
   * @see org.databiosphere.workspacedataservice.dataimport.pfb.PfbQuartzJob
   * @see org.databiosphere.workspacedataservice.dataimport.tdr.TdrManifestQuartzJob
   * @return the configured value
   */
  public boolean isSucceedOnCompletion() {
    return succeedOnCompletion;
  }

  public void setSucceedOnCompletion(boolean succeedOnCompletion) {
    this.succeedOnCompletion = succeedOnCompletion;
  }

  /**
   * Accepted sources for imported files. This includes configured sources as well as default /
   * always allowed sources (GCS buckets, Azure storage containers, and S3 buckets).
   */
  public Set<AllowedImportSource> getAllowedImportSources() {
    return Sets.union(defaultAllowedImportSources, allowedImportSources);
  }

  public void setAllowedImportSources(String allowedImportSources) {
    this.allowedImportSources =
        isBlank(allowedImportSources)
            ? Collections.emptySet()
            : Arrays.stream(allowedImportSources.split(","))
                .map(AllowedImportSource::new)
                .collect(Collectors.toSet());
  }

  /** Dictates the sink where BatchWriteService should write records after import. */
  public enum RecordSinkMode {
    WDS("wds"),
    RAWLS("rawls");
    private final String value;

    RecordSinkMode(String value) {
      this.value = value;
    }

    static RecordSinkMode fromValue(String value) {
      for (RecordSinkMode mode : RecordSinkMode.values()) {
        if (mode.value.equals(value)) {
          return mode;
        }
      }
      throw new RuntimeException("Unknown RecordSinkMode value: %s".formatted(value));
    }
  }

  public class AllowedImportSource {
    private String hostPattern;

    public AllowedImportSource(String hostPattern) {
      this.hostPattern = hostPattern;
    }

    public boolean matchesUrl(URI url) {
      if (hostPattern.startsWith("*")) {
        return url.getHost().endsWith(hostPattern.substring(1));
      } else {
        return url.getHost().equals(hostPattern);
      }
    }
  }
}

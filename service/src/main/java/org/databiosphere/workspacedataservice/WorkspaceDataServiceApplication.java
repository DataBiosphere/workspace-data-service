package org.databiosphere.workspacedataservice;

import com.microsoft.applicationinsights.attach.ApplicationInsights;
import org.databiosphere.workspacedataservice.config.TwdsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(
    scanBasePackages = {
      // this codebase
      "org.databiosphere.workspacedataservice",
      // terra-common-lib transaction management and DB retry configuration
      "bio.terra.common.retry.transaction"
    })
@EnableRetry
@EnableTransactionManagement
@EnableCaching
@EnableConfigurationProperties(TwdsProperties.class)
public class WorkspaceDataServiceApplication {

  public static void main(String[] args) {
    ApplicationInsights.attach();
    SpringApplication.run(WorkspaceDataServiceApplication.class, args);
  }
}

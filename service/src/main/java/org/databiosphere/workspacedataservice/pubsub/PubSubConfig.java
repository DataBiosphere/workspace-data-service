package org.databiosphere.workspacedataservice.pubsub;

import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PubSubConfig {

  // when Pub/Sub autoconfiguration is enabled, use the real ImportPubSub bean
  @Bean
  @ConditionalOnProperty(
      name = "spring.cloud.gcp.pubsub.enabled",
      havingValue = "true",
      matchIfMissing = true)
  PubSub applicationDefaultCredentialsPubSub(
      GcpProjectIdProvider projectIdProvider,
      PubSubTemplate pubSubTemplate,
      @Value("${spring.cloud.gcp.pubsub.topic}") String topic) {
    return new ImportPubSub(pubSubTemplate, topic, projectIdProvider.getProjectId());
  }

  // when Pub/Sub autoconfiguration is disabled, use a noop bean which has no other dependencies
  @Bean
  @ConditionalOnProperty(
      name = "spring.cloud.gcp.pubsub.enabled",
      havingValue = "false",
      matchIfMissing = false)
  PubSub noopPubSub() {
    return new NoopPubSub();
  }
}

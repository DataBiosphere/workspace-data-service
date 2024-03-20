package org.databiosphere.workspacedataservice.controller;

import org.databiosphere.workspacedataservice.annotations.DeploymentMode.ControlPlane;
import org.databiosphere.workspacedataservice.pubsub.JobStatusUpdate;
import org.databiosphere.workspacedataservice.pubsub.PubSubMessage;
import org.databiosphere.workspacedataservice.pubsub.PubSubRequest;
import org.databiosphere.workspacedataservice.service.JobService;
import org.databiosphere.workspacedataservice.service.model.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@ControlPlane
@RestController
public class PubSubController {
  private static final Logger LOGGER = LoggerFactory.getLogger(PubSubController.class);
  private final JobService jobService;

  public PubSubController(JobService jobService) {
    this.jobService = jobService;
  }

  @PostMapping("/pubsub/import-status")
  public ResponseEntity<String> receiveImportNotification(@RequestBody PubSubRequest request) {
    PubSubMessage message = request.message();
    LOGGER.info(
        "Received PubSub message: {}, published {}", message.messageId(), message.publishTime());
    try {
      JobStatusUpdate update = JobStatusUpdate.createFromPubSubMessage(request.message());
      LOGGER.info(
          "Received status update for job {}: {} -> {}",
          update.jobId(),
          update.currentStatus(),
          update.currentStatus());
      jobService.processStatusUpdate(update);
      return new ResponseEntity<>(HttpStatus.OK);
    } catch (ValidationException e) {
      // Return a successful status for invalid updates to prevent PubSub from retrying the request.
      // https://cloud.google.com/pubsub/docs/push#receive_push
      LOGGER.error("Error processing status update: {}", e.getMessage());
      return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }
  }
}

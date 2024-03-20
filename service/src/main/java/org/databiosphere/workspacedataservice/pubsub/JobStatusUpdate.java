package org.databiosphere.workspacedataservice.pubsub;

import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.databiosphere.workspacedataservice.generated.GenericJobServerModel.StatusEnum;

public record JobStatusUpdate(
    UUID jobId, StatusEnum currentStatus, StatusEnum newStatus, @Nullable String errorMessage) {
  public static JobStatusUpdate createFromPubSubMessage(PubSubMessage message) {
    Map<String, String> attributes = message.attributes();
    UUID jobId = UUID.fromString(attributes.get("import_id"));
    StatusEnum newStatus = rawlsStatusToJobStatus(attributes.get("new_status"));
    StatusEnum currentStatus = rawlsStatusToJobStatus(attributes.get("current_status"));
    String errorMessage = attributes.get("error_message");
    return new JobStatusUpdate(jobId, currentStatus, newStatus, errorMessage);
  }

  private static StatusEnum rawlsStatusToJobStatus(String rawlsStatus) {
    return switch (rawlsStatus) {
      case "ReadyForUpsert" -> StatusEnum.RUNNING;
      case "Upserting" -> StatusEnum.RUNNING;
      case "Done" -> StatusEnum.SUCCEEDED;
      case "Error" -> StatusEnum.ERROR;
      default -> throw new RuntimeException(
          "Unknown Rawls import status: %s".formatted(rawlsStatus));
    };
  }
}

package org.databiosphere.workspacedataservice.rawls;

import static org.databiosphere.workspacedataservice.annotations.DeploymentMode.*;

import bio.terra.workspace.model.DataRepoSnapshotResource;
import java.util.Objects;
import java.util.UUID;
import org.databiosphere.workspacedataservice.sam.TokenContextUtil;
import org.databiosphere.workspacedataservice.shared.model.BearerToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ControlPlane
public class RawlsClient {

  private final String rawlsUrl;
  // TODO: consider using RestClient instead of RestTemplate
  private final RestTemplate restTemplate;

  private static final Logger LOGGER = LoggerFactory.getLogger(RawlsClient.class);

  @Autowired
  public RawlsClient(String rawlsUrl, RestTemplate restTemplate) {
    this.rawlsUrl = rawlsUrl;
    this.restTemplate = restTemplate;
  }

  public SnapshotListResponse enumerateDataRepoSnapshotReferences(
      UUID workspaceId, int offset, int limit) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(rawlsUrl)
            .pathSegment("api", "workspaces", workspaceId.toString(), "snapshots", "v2")
            .queryParam("offset", offset)
            .queryParam("limit", limit);

    ResponseEntity<SnapshotListResponse> response =
        restTemplate.exchange(
            builder.build().toUri(),
            HttpMethod.GET,
            new HttpEntity<>(getAuthedHeaders()),
            SnapshotListResponse.class);
    return response.getBody();
  }

  // TODO: (AJ-1705) Add cloning instructions COPY_REFERENCE and a purpose=policy
  // key-value pair to the reference’s properties
  public void createSnapshotReference(UUID workspaceId, UUID snapshotId) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(rawlsUrl)
            .pathSegment("api", "workspaces", workspaceId.toString(), "snapshots", "v2");

    restTemplate.exchange(
        builder.build().toUri(),
        HttpMethod.POST,
        new HttpEntity<>(NamedDataRepoSnapshot.forSnapshotId(snapshotId), getAuthedHeaders()),
        DataRepoSnapshotResource.class);
  }

  // Get the user's token from the context and attach it to headers
  private HttpHeaders getAuthedHeaders() {
    HttpHeaders headers = new HttpHeaders();
    BearerToken token = TokenContextUtil.getToken();

    if (token.nonEmpty()) {
      LOGGER.debug("setting access token for rawls request");
      headers.setBearerAuth(Objects.requireNonNull(token.getValue()));
    } else {
      LOGGER.warn("No access token found for rawls request.");
    }
    return headers;
  }
}

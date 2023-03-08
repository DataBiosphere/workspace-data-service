package org.databiosphere.workspacedataservice.sam;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Objects;

import static org.databiosphere.workspacedataservice.sam.BearerTokenFilter.ATTRIBUTE_NAME_TOKEN;
import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

/**
 * Implementation of SamClientFactory that creates a Sam ApiClient, initializes that client with
 * the url to Sam, adds the current user's access token to the client, and then returns the
 * ResourcesApi from that client. ResourcesApi is the part of the Sam client used by WDS.
 */
public class HttpSamClientFactory implements SamClientFactory {

    private final String samUrl;

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpSamClientFactory.class);


    public HttpSamClientFactory(String samUrl) {
        this.samUrl = samUrl;
    }

    private ApiClient getApiClient() {
        // create a new Sam client
        ApiClient apiClient = new ApiClient();
        // initialize the client with the url to Sam
        if (StringUtils.isNotBlank(samUrl)) {
            apiClient.setBasePath(samUrl);
        }
        // grab the current user's bearer token (see BearerTokenFilter)
        Object token = RequestContextHolder.currentRequestAttributes()
                .getAttribute(ATTRIBUTE_NAME_TOKEN, SCOPE_REQUEST);
        // add the user's bearer token to the client
        if (!Objects.isNull(token)) {
            LOGGER.debug("setting access token for Sam request: {}", BearerTokenFilter.loggableToken(token.toString()));
            apiClient.setAccessToken(token.toString());
        } else {
            LOGGER.warn("No access token found for Sam request.");
        }
        // return the client
        return apiClient;
    }

    /**
     * Get a ResourcesApi Sam client, initialized with the url to Sam and the current user's
     * access token, if any
     * @return the usable Sam client
     */
    public ResourcesApi getResourcesApi() {
        ApiClient apiClient = getApiClient();
        ResourcesApi resourcesApi = new ResourcesApi();
        resourcesApi.setApiClient(apiClient);
        return resourcesApi;
    }

}

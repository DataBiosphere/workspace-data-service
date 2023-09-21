package org.databiosphere.workspacedataservice.activitylog;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.databiosphere.workspacedataservice.sam.BearerTokenFilter;
import org.databiosphere.workspacedataservice.sam.SamClientFactory;
import org.databiosphere.workspacedataservice.service.InstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@ActiveProfiles(profiles = "mock-sam")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
public class ActivityEventBuilderTest {

  @Autowired InstanceService instanceService;

  @MockBean SamClientFactory mockSamClientFactory;

  final UsersApi mockUsersApi = Mockito.mock(UsersApi.class);
  final ResourcesApi mockResourcesApi = Mockito.mock(ResourcesApi.class);

  @BeforeEach
  void beforeEach() {
    given(mockSamClientFactory.getUsersApi(any())).willReturn(mockUsersApi);
    given(mockSamClientFactory.getResourcesApi(any())).willReturn(mockResourcesApi);
  }

  @Test
  void testTokenResolutionViaSam(CapturedOutput output) throws ApiException {
    // set up the Sam mocks
    UserStatusInfo userStatusInfo = new UserStatusInfo();
    userStatusInfo.userSubjectId("userid-for-unit-tests-hello!");
    when(mockUsersApi.getUserStatusInfo()).thenReturn(userStatusInfo);
    when(mockResourcesApi.resourcePermissionV2(any(), any(), any())).thenReturn(true);

    // instanceId
    UUID instanceId = UUID.randomUUID();

    // ensure we have a token in the current request; else we'll get "anonymous" for our user
    RequestAttributes currentAttributes = RequestContextHolder.currentRequestAttributes();
    currentAttributes.setAttribute(
        BearerTokenFilter.ATTRIBUTE_NAME_TOKEN, "fakey-token", SCOPE_REQUEST);
    RequestContextHolder.setRequestAttributes(currentAttributes);

    // create an instance; this will trigger logging
    instanceService.createInstance(instanceId, "v0.2");

    // did we log the
    assertThat(output.getOut())
        .contains(
            "user userid-for-unit-tests-hello! created 1 instance(s) with id(s) [%s]"
                .formatted(instanceId));
  }
}

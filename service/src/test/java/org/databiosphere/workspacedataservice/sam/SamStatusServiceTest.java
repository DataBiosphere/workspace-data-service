package org.databiosphere.workspacedataservice.sam;

import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.broadinstitute.dsde.workbench.client.sam.model.SystemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.stream.Stream;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest
class SamStatusServiceTest {

    @Autowired
    private SamStatusService samStatusService;

    @MockBean
    SamClientFactory mockSamClientFactory;

    // mock for the StatusApi class inside the Sam client; since this is not a Spring bean we have to mock it manually
    StatusApi mockStatusApi = Mockito.mock(StatusApi.class);
    // mock for Health.Builder class that Spring Boot Actuator relies on to determine overall health of an application.
    Health.Builder mockHealthBuilder = Mockito.mock(Health.Builder.class);

    @BeforeEach
    void beforeEach() throws ApiException {
        // return the mock StatusApi from the mock SamClientFactory
        given(mockSamClientFactory.getStatusApi()).willReturn(mockStatusApi);
        Mockito.clearInvocations(mockStatusApi);
        Mockito.clearInvocations(mockHealthBuilder);
    }

    @Test
    void testSamHealthyCall() throws Exception {
        SystemStatus status = new SystemStatus();

        status.ok(true);
        when(mockStatusApi.getSystemStatus()).thenReturn(status);
        samStatusService.doHealthCheck(mockHealthBuilder);
        verify(mockHealthBuilder, times(1)).withDetail("ok", true);
        verify(mockStatusApi, times(1)).getSystemStatus();
    }
}

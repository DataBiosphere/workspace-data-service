package org.databiosphere.workspacedataservice.leonardo;

import org.broadinstitute.dsde.workbench.client.leonardo.ApiException;
import org.broadinstitute.dsde.workbench.client.leonardo.api.AppsV2Api;
import org.broadinstitute.dsde.workbench.client.leonardo.model.AppStatus;
import org.broadinstitute.dsde.workbench.client.leonardo.model.AppType;
import org.broadinstitute.dsde.workbench.client.leonardo.model.AuditInfo;
import org.broadinstitute.dsde.workbench.client.leonardo.model.ListAppResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DirtiesContext
@SpringBootTest(classes = {LeonardoConfig.class})
@TestPropertySource(properties = {"twds.instance.workspace-id=90e1b179-9f83-4a6f-a8c2-db083df4cd03"})
class LeonardoDaoTest {
    @Autowired
    LeonardoDao leonardoDao;

    @MockBean
    LeonardoClientFactory leonardoClientFactory;

    final AppsV2Api mockAppsApi = mock(AppsV2Api.class);

    @BeforeEach
    void beforeEach() {
        given(leonardoClientFactory.getAppsV2Api(any())).willReturn(mockAppsApi);
    }

    final String expectedUrl = "https://lz28e3b6da2a7f727cae07c307f6c7c11d96da0d1fde444216.servicebus.windows.net/wds-e433ab84-1725-4666-b07e-4ee7ca";
    @Test
    void testWdsUrlNotReturned() throws ApiException {
        final int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
        given(mockAppsApi.listAppsV2(String.valueOf(UUID.randomUUID()), "things", null, null))
                .willThrow(new org.broadinstitute.dsde.workbench.client.leonardo.ApiException(statusCode, "Intentional error thrown for unit test"));
        var exception = assertThrows(LeonardoServiceException.class, () -> leonardoDao.getWdsEndpointUrl(any()));
        assertEquals(statusCode, exception.getRawStatusCode());
    }

    @Test
    void testWdsUrlReturned() {
        var url = buildAppResponseAndCallExtraction(generateListAppResponse("wds", AppStatus.RUNNING,1));
        assertEquals(expectedUrl, url);
    }

    @Test
    void testWdsUrlNotFoundWrongStatus() {
        var url = buildAppResponseAndCallExtraction(generateListAppResponse("wds", AppStatus.DELETED, 1));
        assertNull(url);
    }

    @Test
    void testWdsUrlNotFoundWrongKey() {
        var url = buildAppResponseAndCallExtraction(generateListAppResponse("not-wds", AppStatus.RUNNING, 1));
        assertNull(url);
    }

    @Test
    void testWdsUrlMultiple() {
        // tests the case if there are 2 running wds apps
        var url = buildAppResponseAndCallExtraction(generateListAppResponse("wds", AppStatus.RUNNING,2));
        assertEquals(expectedUrl, url);
    }

    String buildAppResponseAndCallExtraction(List<ListAppResponse> responses ){
        return leonardoDao.extractWdsUrl(responses);
    }

    List<ListAppResponse> generateListAppResponse(String wdsKey, AppStatus wdsStatus, int count) {
        List<ListAppResponse> responseList = new ArrayList<>();
        var url = expectedUrl;
        while(count != 0) {
            ListAppResponse response = mock(ListAppResponse.class);
            Map<String, String> proxyUrls = new HashMap<>();
            proxyUrls.put(wdsKey, url);
            when(response.getProxyUrls()).thenReturn(proxyUrls);
            when(response.getStatus()).thenReturn(wdsStatus);
            when(response.getAppType()).thenReturn(AppType.WDS);
            when(response.getAuditInfo()).thenReturn(mock(AuditInfo.class));
            when(response.getAuditInfo().getCreatedDate()).thenReturn(new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()));
            responseList.add(response);
            count--;

            // only one wds entry will have a valid url, so mark url to be an empty string for all but the first loop through
            url = "";
        }

        return responseList;
    }
}
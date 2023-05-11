package org.databiosphere.workspacedataservice.datarepo;

import bio.terra.datarepo.api.RepositoryApi;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.SnapshotModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(
        classes = {DataRepoConfig.class})
class DataRepoDaoTest {

    @Autowired
    DataRepoDao dataRepoDao;

    @MockBean
    DataRepoClientFactory mockDataRepoClientFactory;

    RepositoryApi mockRepositoryApi = Mockito.mock(RepositoryApi.class);

    @BeforeEach
    void beforeEach() {
        given(mockDataRepoClientFactory.getRepositoryApi()).willReturn(mockRepositoryApi);
    }

    @Test
    void testSnapshotReturned() throws ApiException {
        final SnapshotModel testSnapshot = new SnapshotModel().name("test snapshot").id(UUID.randomUUID());
        given(mockRepositoryApi.retrieveSnapshot(any(), any()))
                .willReturn(testSnapshot);
        assertEquals(testSnapshot, dataRepoDao.getSnapshot(testSnapshot.getId()));
    }

    @Test
    void testErrorThrown() throws ApiException {
        final int statusCode = HttpStatus.UNAUTHORIZED.value();
        given(mockRepositoryApi.retrieveSnapshot(any(), any()))
                .willThrow(new ApiException(statusCode, "Intentional error thrown for unit test"));
        var exception = assertThrows(DataRepoException.class, () -> dataRepoDao.getSnapshot(UUID.randomUUID()));
        assertEquals(statusCode, exception.getRawStatusCode());
    }
}

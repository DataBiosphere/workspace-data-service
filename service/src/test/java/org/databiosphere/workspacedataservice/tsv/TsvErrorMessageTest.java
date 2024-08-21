package org.databiosphere.workspacedataservice.tsv;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.databiosphere.workspacedataservice.TestUtils;
import org.databiosphere.workspacedataservice.common.TestBase;
import org.databiosphere.workspacedataservice.service.CollectionService;
import org.databiosphere.workspacedataservice.service.RecordOrchestratorService;
import org.databiosphere.workspacedataservice.service.model.exception.InvalidTsvException;
import org.databiosphere.workspacedataservice.shared.model.RecordType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
class TsvErrorMessageTest extends TestBase {

  @Autowired CollectionService collectionService;
  @Autowired RecordOrchestratorService recordOrchestratorService;
  @Autowired NamedParameterJdbcTemplate namedTemplate;

  private UUID collectionId;

  private static final String VERSION = "v0.2";

  @BeforeEach
  void setUp() {
    collectionId = UUID.randomUUID();
    collectionService.createCollection(collectionId, VERSION);
  }

  @AfterEach
  void tearDown() {
    TestUtils.cleanAllCollections(collectionService, namedTemplate);
  }

  @ParameterizedTest(name = "The malformed TSV file '{0}' should throw a helpful error")
  @ValueSource(
      strings = {
        "tsv/errors/column-separator.tsv",
        "tsv/errors/empty-header-line.tsv",
        "tsv/errors/invalid-middle-byte.tsv",
        "tsv/errors/too-many-entries.tsv"
      })
  void testBadTsvFile(String badTsvFile) throws Exception {
    try (InputStream inputStream = ClassLoader.getSystemResourceAsStream(badTsvFile)) {

      MockMultipartFile tsvUpload =
          new MockMultipartFile("some_file", "some_file", MediaType.TEXT_PLAIN_VALUE, inputStream);

      Exception actual =
          assertThrows(
              InvalidTsvException.class,
              () ->
                  recordOrchestratorService.tsvUpload(
                      collectionId,
                      VERSION,
                      RecordType.valueOf("will_error"),
                      Optional.empty(),
                      tsvUpload));

      assertThat(
          actual.getMessage(),
          startsWith(
              "Error reading TSV. Please check the format of your upload. Underlying error is"));
    }
  }
}

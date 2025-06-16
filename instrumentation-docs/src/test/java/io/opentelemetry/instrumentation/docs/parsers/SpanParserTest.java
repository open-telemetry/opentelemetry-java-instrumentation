package io.opentelemetry.instrumentation.docs.parsers;

import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@SuppressWarnings("NullAway")
class SpanParserTest {

  @Test
  void getSpansFromFilesCombinesFilesCorrectly(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    String file1Content = """
        when: default
        spans_by_scope:
          - scope: test
            spans:
              - span_kind: INTERNAL
                attributes:
          - scope: io.opentelemetry.clickhouse-client-0.5
            spans:
              - span_kind: SERVER
                attributes:
                  - name: server.address
                    type: STRING
      """;

    String file2Content = """
        when: default
        spans_by_scope:
          - scope: test
            spans:
              - span_kind: INTERNAL
                attributes:
          - scope: io.opentelemetry.clickhouse-client-0.5
            spans:
              - span_kind: CLIENT
                attributes:
                  - name: db.statement
                    type: STRING
                  - name: server.port
                    type: LONG
                  - name: db.system
                    type: STRING
                  - name: server.address
                    type: STRING
                  - name: db.name
                    type: STRING
                  - name: db.operation
                    type: STRING
      """;

    Files.writeString(telemetryDir.resolve("spans-1.yaml"), file1Content);
    Files.writeString(telemetryDir.resolve("spans-2.yaml"), file2Content);

    try (MockedStatic<FileManager> fileManagerMock = mockStatic(FileManager.class)) {
      fileManagerMock
          .when(
              () -> FileManager.readFileToString(telemetryDir.resolve("spans-1.yaml").toString()))
          .thenReturn(file1Content);
      fileManagerMock
          .when(
              () -> FileManager.readFileToString(telemetryDir.resolve("spans-2.yaml").toString()))
          .thenReturn(file2Content);

      Map<String, EmittedSpans> result = SpanParser.getSpansByScopeFromFiles(tempDir.toString(), "");

      EmittedSpans spans = result.get("default");
      assertThat(spans.getSpansByScope()).hasSize(2);

      List<EmittedSpans.Span> clickHouseSpans =
          spans.getSpansByScope().stream().filter(item -> item.getScope().equals("io.opentelemetry.clickhouse-client-0.5")).map(
              EmittedSpans.SpansByScope::getSpans).findFirst().orElse(null);
      assertThat(clickHouseSpans).hasSize(2);


      List<EmittedSpans.Span> testSpans =
          spans.getSpansByScope().stream().filter(item -> item.getScope().equals("test")).map(
              EmittedSpans.SpansByScope::getSpans).findFirst().orElse(null);
      // deduped should have only one span
      assertThat(testSpans).hasSize(1);
    }
  }
}

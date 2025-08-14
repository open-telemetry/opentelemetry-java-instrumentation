/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

@SuppressWarnings("NullAway")
class SpanParserTest {

  @Test
  void getSpansFromFilesCombinesFilesCorrectly(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    String file1Content =
        """
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

    String file2Content =
        """
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
    // duplicate span contents to test deduplication
    Files.writeString(telemetryDir.resolve("spans-3.yaml"), file2Content);

    try (MockedStatic<FileManager> fileManagerMock = mockStatic(FileManager.class)) {
      fileManagerMock
          .when(() -> FileManager.readFileToString(telemetryDir.resolve("spans-1.yaml").toString()))
          .thenReturn(file1Content);
      fileManagerMock
          .when(() -> FileManager.readFileToString(telemetryDir.resolve("spans-2.yaml").toString()))
          .thenReturn(file2Content);
      fileManagerMock
          .when(() -> FileManager.readFileToString(telemetryDir.resolve("spans-3.yaml").toString()))
          .thenReturn(file2Content);

      Map<String, EmittedSpans> result =
          EmittedSpanParser.getSpansByScopeFromFiles(tempDir.toString(), "");

      EmittedSpans spans = result.get("default");
      assertThat(spans.getSpansByScope()).hasSize(2);

      List<EmittedSpans.Span> clickHouseSpans =
          spans.getSpansByScope().stream()
              .filter(item -> item.getScope().equals("io.opentelemetry.clickhouse-client-0.5"))
              .map(EmittedSpans.SpansByScope::getSpans)
              .findFirst()
              .orElse(null);
      assertThat(clickHouseSpans).hasSize(2);

      EmittedSpans.Span serverSpan =
          clickHouseSpans.stream()
              .filter(item -> item.getSpanKind().equals("SERVER"))
              .findFirst()
              .orElse(null);
      EmittedSpans.Span clientSpan =
          clickHouseSpans.stream()
              .filter(item -> item.getSpanKind().equals("CLIENT"))
              .findFirst()
              .orElse(null);

      assertThat(serverSpan.getAttributes()).hasSize(1);
      assertThat(clientSpan.getAttributes()).hasSize(6);

      List<EmittedSpans.Span> testSpans =
          spans.getSpansByScope().stream()
              .filter(item -> item.getScope().equals("test"))
              .map(EmittedSpans.SpansByScope::getSpans)
              .findFirst()
              .orElse(null);
      // deduped should have only one span
      assertThat(testSpans).hasSize(1);
    }
  }

  @Test
  void testSpanAggregatorFiltersAndAggregatesCorrectly() {
    String targetScopeName = "my-instrumentation-scope";

    EmittedSpans.Span span1 =
        new EmittedSpans.Span(
            "CLIENT",
            List.of(
                new TelemetryAttribute("my.operation", "STRING"),
                new TelemetryAttribute("http.request.header.x-test-request", "STRING_ARRAY"),
                new TelemetryAttribute("http.response.header.x-test-response", "STRING_ARRAY")));
    EmittedSpans.Span span2 =
        new EmittedSpans.Span(
            "SERVER",
            List.of(
                new TelemetryAttribute("my.operation", "STRING"),
                new TelemetryAttribute("test-baggage-key-1", "STRING"),
                new TelemetryAttribute("test-baggage-key-2", "STRING")));

    // Create test span for a different scope (should be filtered out)
    EmittedSpans.Span testSpan =
        new EmittedSpans.Span(
            "INTERNAL", List.of(new TelemetryAttribute("my.operation", "STRING")));

    EmittedSpans.SpansByScope targetSpansByScope =
        new EmittedSpans.SpansByScope(targetScopeName, List.of(span1, span2));
    EmittedSpans.SpansByScope otherSpansByScope =
        new EmittedSpans.SpansByScope("other-scope", List.of(testSpan));

    EmittedSpans emittedSpans =
        new EmittedSpans("default", List.of(targetSpansByScope, otherSpansByScope));

    // Aggregate spans - only target scope should be included
    Map<String, Map<String, Set<TelemetryAttribute>>> spans =
        SpanParser.SpanAggregator.aggregateSpans("default", emittedSpans, targetScopeName);

    Map<String, List<EmittedSpans.Span>> result =
        SpanParser.SpanAggregator.buildFilteredSpans(spans);

    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get("default")).isNotNull();
    assertThat(result.get("default").size()).isEqualTo(2); // CLIENT and SERVER spans

    // Verify span kinds are preserved
    List<String> spanKinds =
        result.get("default").stream().map(EmittedSpans.Span::getSpanKind).toList();
    assertThat(spanKinds).containsExactlyInAnyOrder("CLIENT", "SERVER");

    // Verify test attributes are filtered out
    List<TelemetryAttribute> clientAttributes =
        result.get("default").stream()
            .filter(span -> span.getSpanKind().equals("CLIENT"))
            .flatMap(span -> span.getAttributes().stream())
            .toList();
    assertThat(clientAttributes)
        .hasSize(1)
        .extracting(TelemetryAttribute::getName)
        .containsExactly("my.operation");

    List<TelemetryAttribute> serverAttributes =
        result.get("default").stream()
            .filter(span -> span.getSpanKind().equals("SERVER"))
            .flatMap(span -> span.getAttributes().stream())
            .toList();

    assertThat(serverAttributes)
        .hasSize(1)
        .extracting(TelemetryAttribute::getName)
        .containsExactly("my.operation");
  }

  @Test
  void getSpansFromFilesIncludesAllowListedScopes(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    String file1Content =
        """
        when: default
        spans_by_scope:
          - scope: io.opentelemetry.grpc-1.6
            spans:
              - span_kind: CLIENT
                attributes:
                  - name: rpc.system
                    type: STRING
                  - name: rpc.grpc.status_code
                    type: LONG
                  - name: server.port
                    type: LONG
                  - name: rpc.method
                    type: STRING
                  - name: rpc.service
                    type: STRING
                  - name: server.address
                    type: STRING
              - span_kind: SERVER
                attributes:
                  - name: rpc.system
                    type: STRING
                  - name: rpc.grpc.status_code
                    type: LONG
                  - name: server.port
                    type: LONG
                  - name: rpc.method
                    type: STRING
                  - name: rpc.service
                    type: STRING
                  - name: server.address
                    type: STRING
      """;

    Files.writeString(telemetryDir.resolve("spans-1.yaml"), file1Content);

    try (MockedStatic<FileManager> fileManagerMock = mockStatic(FileManager.class)) {
      fileManagerMock
          .when(() -> FileManager.readFileToString(telemetryDir.resolve("spans-1.yaml").toString()))
          .thenReturn(file1Content);

      Map<String, EmittedSpans> fileContents =
          EmittedSpanParser.getSpansByScopeFromFiles(tempDir.toString(), "");

      EmittedSpans emittedSpans = fileContents.get("default");

      // Aggregate spans - only target scope should be included
      Map<String, Map<String, Set<TelemetryAttribute>>> spans =
          SpanParser.SpanAggregator.aggregateSpans(
              "default", emittedSpans, "io.opentelemetry.armeria-grpc-1.14");

      Map<String, List<EmittedSpans.Span>> result =
          SpanParser.SpanAggregator.buildFilteredSpans(spans);

      assertThat(result.size()).isEqualTo(1);
      assertThat(result.get("default")).isNotNull();
      assertThat(result.get("default").size()).isEqualTo(2);
    }
  }
}

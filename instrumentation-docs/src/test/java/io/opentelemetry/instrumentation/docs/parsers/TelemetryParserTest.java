/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TelemetryParserTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void springKafkaTelemetry(boolean standaloneScopePresent, @TempDir Path tempDir)
      throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));
    String scopes =
        """
        scopes:
          - name: io.opentelemetry.spring-kafka-2.7
            schemaUrl: https://opentelemetry.io/schemas/1.21.0
            attributes:
              origin: agent
          - name: io.opentelemetry.kafka-2.6
          - name: io.opentelemetry.spring-kafka-2.9
        """;
    if (standaloneScopePresent) {
      scopes +=
          """
            - name: io.opentelemetry.spring-kafka-2.8
              schemaUrl: https://opentelemetry.io/schemas/1.22.0
          """;
    }
    Files.writeString(telemetryDir.resolve("scope-test.yaml"), scopes);
    Files.writeString(
        telemetryDir.resolve("spans-test.yaml"),
        """
        when: default
        spans_by_scope:
          - scope: io.opentelemetry.spring-kafka-2.7
            spans:
              - span_kind: CONSUMER
                attributes:
                  - name: agent.attribute
                    type: STRING
          - scope: io.opentelemetry.spring-kafka-2.8
            spans:
              - span_kind: INTERNAL
                attributes:
                  - name: library.attribute
                    type: STRING
          - scope: io.opentelemetry.kafka-2.6
            spans:
              - span_kind: CLIENT
                attributes:
                  - name: kafka.attribute
                    type: STRING
          - scope: io.opentelemetry.spring-kafka-2.9
            spans:
              - span_kind: SERVER
                attributes:
                  - name: other.version
                    type: STRING
        """);
    Files.writeString(
        telemetryDir.resolve("metrics-test.yaml"),
        """
        when: default
        metrics_by_scope:
          - scope: io.opentelemetry.spring-kafka-2.7
            metrics:
              - name: agent.metric
                type: counter
          - scope: io.opentelemetry.spring-kafka-2.8
            metrics:
              - name: library.metric
                type: counter
          - scope: io.opentelemetry.kafka-2.6
            metrics:
              - name: kafka.metric
                type: counter
          - scope: io.opentelemetry.spring-kafka-2.9
            metrics:
              - name: other.version.metric
                type: counter
        """);

    InstrumentationModule module =
        new InstrumentationModule.Builder("spring-kafka-2.8").srcPath("").build();
    FileManager fileManager = new FileManager(tempDir);
    InstrumentationScopeInfo scope = EmittedScopeParser.getScope(fileManager, module);
    assertThat(scope).isNotNull();
    assertThat(scope.getName())
        .isEqualTo(
            standaloneScopePresent
                ? "io.opentelemetry.spring-kafka-2.8"
                : "io.opentelemetry.spring-kafka-2.7");
    assertThat(scope.getSchemaUrl())
        .isEqualTo(
            standaloneScopePresent
                ? "https://opentelemetry.io/schemas/1.22.0"
                : "https://opentelemetry.io/schemas/1.21.0");
    if (!standaloneScopePresent) {
      assertThat(scope.getAttributes().get(stringKey("origin"))).isEqualTo("agent");
    }
    module.setScopeInfo(scope);

    List<EmittedSpans.Span> spans = SpanParser.getSpans(module, fileManager).get("default");
    assertThat(spans)
        .extracting(EmittedSpans.Span::getSpanKind)
        .containsExactlyInAnyOrder("CONSUMER", "INTERNAL");
    assertThat(spans.stream().flatMap(span -> span.getAttributes().stream()))
        .extracting(TelemetryAttribute::getName)
        .containsExactlyInAnyOrder("agent.attribute", "library.attribute");
    assertThat(MetricParser.getMetrics(module, fileManager).get("default"))
        .extracting(EmittedMetrics.Metric::getName)
        .containsExactlyInAnyOrder("agent.metric", "library.metric");
  }

  @Test
  void springKafkaScopeDoesNotMatchUnrelatedScopes(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));
    Files.writeString(
        telemetryDir.resolve("scope-test.yaml"),
        """
        scopes:
          - name: io.opentelemetry.kafka-2.6
          - name: io.opentelemetry.spring-kafka-2.9
        """);
    InstrumentationModule module =
        new InstrumentationModule.Builder("spring-kafka-2.8").srcPath("").build();

    assertThat(EmittedScopeParser.getScope(new FileManager(tempDir), module)).isNull();
    assertThat(
            TelemetryParser.scopeIsValid(
                "io.opentelemetry.spring-kafka-2.7", "io.opentelemetry.spring-kafka-2.9"))
        .isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "io.opentelemetry.ratpack-1.4, true",
    "io.opentelemetry.ratpack-1.7, false",
    "io.opentelemetry.netty-4.1, false"
  })
  void ratpackScopes(String telemetryScope, boolean expected) {
    assertThat(TelemetryParser.scopeIsValid(telemetryScope, "io.opentelemetry.ratpack-1.4"))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "io.opentelemetry.mongo-3.1, true",
    "io.opentelemetry.mongo-3.7, false",
    "io.opentelemetry.mongo-4.0, false",
    "io.opentelemetry.mongo-async-3.3, false",
    "io.opentelemetry.jdbc, false"
  })
  void mongoScopes(String telemetryScope, boolean expected) {
    assertThat(TelemetryParser.scopeIsValid(telemetryScope, "io.opentelemetry.mongo-3.1"))
        .isEqualTo(expected);
  }

  @Test
  void normalizeWhenConditionStripsQuotes() {
    String content =
        """
        when: "otel.instrumentation.common.view-telemetry.enabled=true,otel.instrumentation.jsp.experimental-span-attributes=true"
        metrics_by_scope:
          - scope: io.opentelemetry.jsp-2.3
        """;

    String result = TelemetryParser.normalizeWhenCondition(content);

    assertThat(result)
        .isEqualTo(
            "otel.instrumentation.common.view-telemetry.enabled=true,otel.instrumentation.jsp.experimental-span-attributes=true");
  }

  @Test
  void normalizeWhenConditionHandlesUnquotedValue() {
    String content =
        """
        when: default
        metrics_by_scope:
          - scope: io.opentelemetry.test
        """;

    String result = TelemetryParser.normalizeWhenCondition(content);

    assertThat(result).isEqualTo("default");
  }

  @Test
  void normalizeWhenConditionReturnsEmptyForNull() {
    String result = TelemetryParser.normalizeWhenCondition(null);

    assertThat(result).isEmpty();
  }

  @Test
  void normalizeWhenConditionHandlesComplexConditions() {
    String content =
        """
        when: "config1=value1,config2=value2,config3=value3"
        spans_by_scope:
          - scope: io.opentelemetry.test
        """;

    String result = TelemetryParser.normalizeWhenCondition(content);

    assertThat(result).isEqualTo("config1=value1,config2=value2,config3=value3");
  }
}

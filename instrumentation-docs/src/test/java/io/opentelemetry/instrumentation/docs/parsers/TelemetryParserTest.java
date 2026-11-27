/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TelemetryParserTest {

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

  @ParameterizedTest
  @CsvSource({
    "jaxrs-2.0-cxf-3.2, jaxrs-annotations-2.0",
    "jaxrs-2.0-jersey-2.0, jaxrs-annotations-2.0",
    "jaxrs-2.0-resteasy-3.0, jaxrs-annotations-2.0",
    "jaxrs-2.0-resteasy-3.1, jaxrs-annotations-2.0",
    "jaxrs-3.0-jersey-3.0, jaxrs-annotations-3.0",
    "jaxrs-3.0-resteasy-6.0, jaxrs-annotations-3.0"
  })
  void jaxrsProviderScopes(String module, String annotationScope) {
    assertThat(
            TelemetryParser.scopeIsValid(
                "io.opentelemetry." + annotationScope, "io.opentelemetry." + module))
        .isTrue();
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

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeName;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeVersion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.logs.TestLogRecordData;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

class InstrumentationScopeAssertionsTest {

  private static final InstrumentationScopeInfo SCOPE =
      InstrumentationScopeInfo.builder("test-scope")
          .setVersion("1.2.3")
          .setSchemaUrl("https://opentelemetry.io/schemas/1.37.0")
          .build();

  private static final InstrumentationScopeInfo EMPTY_SCOPE =
      InstrumentationScopeInfo.create("empty-scope");

  @ParameterizedTest
  @MethodSource("telemetryData")
  void matchingScope(Object data) {
    assertThat(data)
        .satisfies(hasScopeName("test-scope"))
        .satisfies(hasScopeVersion("1.2.3"))
        .satisfies(hasScopeSchemaUrl("https://opentelemetry.io/schemas/1.37.0"));
  }

  @ParameterizedTest
  @MethodSource("telemetryData")
  void mismatchedScope(Object data) {
    assertThatThrownBy(() -> assertThat(data).satisfies(hasScopeName("other")))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertThat(data).satisfies(hasScopeVersion("other")))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertThat(data).satisfies(hasScopeSchemaUrl("other")))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertThat(data).satisfies(hasScopeVersion(null)))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> assertThat(data).satisfies(hasScopeSchemaUrl(null)))
        .isInstanceOf(AssertionError.class);
  }

  @ParameterizedTest
  @MethodSource("telemetryDataWithoutVersionOrSchemaUrl")
  void nullVersionAndSchemaUrl(Object data) {
    assertThat(data).satisfies(hasScopeVersion(null)).satisfies(hasScopeSchemaUrl(null));
    assertThatThrownBy(() -> assertThat(data).satisfies(hasScopeVersion("1.2.3")))
        .isInstanceOf(AssertionError.class);
  }

  @ParameterizedTest
  @MethodSource("unsupportedData")
  @NullSource
  void unsupportedInput(Object data) {
    assertThatThrownBy(() -> assertThat(data).satisfies(hasScopeName("test-scope")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Stream<Object> telemetryData() {
    return dataWithScope(SCOPE);
  }

  private static Stream<Object> telemetryDataWithoutVersionOrSchemaUrl() {
    return dataWithScope(EMPTY_SCOPE);
  }

  private static Stream<Object> unsupportedData() {
    return Stream.of("not telemetry", SCOPE);
  }

  private static Stream<Object> dataWithScope(InstrumentationScopeInfo scope) {
    return Stream.of(span(scope), metric(scope), logRecord(scope));
  }

  private static SpanData span(InstrumentationScopeInfo scope) {
    return TestSpanData.builder()
        .setName("span")
        .setSpanContext(SpanContext.getInvalid())
        .setKind(SpanKind.INTERNAL)
        .setStatus(StatusData.unset())
        .setHasEnded(true)
        .setStartEpochNanos(0)
        .setEndEpochNanos(1)
        .setInstrumentationScopeInfo(scope)
        .build();
  }

  private static MetricData metric(InstrumentationScopeInfo scope) {
    return ImmutableMetricData.createLongGauge(
        Resource.empty(), scope, "metric", "", "", ImmutableGaugeData.empty());
  }

  private static LogRecordData logRecord(InstrumentationScopeInfo scope) {
    return TestLogRecordData.builder().setInstrumentationScopeInfo(scope).build();
  }
}

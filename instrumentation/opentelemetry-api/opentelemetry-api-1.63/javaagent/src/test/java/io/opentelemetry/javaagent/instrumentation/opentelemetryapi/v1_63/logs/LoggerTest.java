/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_63.logs;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.ValueType;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.trace.IdGenerator;
import java.time.Instant;
import org.assertj.core.api.AbstractCharSequenceAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;

class LoggerTest {

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  private String instrumentationName;
  private Logger logger;

  @BeforeEach
  void setupLogger(TestInfo test) {
    instrumentationName = "test-" + test.getDisplayName();
    logger =
        GlobalOpenTelemetry.get()
            .getLogsBridge()
            .loggerBuilder(instrumentationName)
            .setInstrumentationVersion("1.2.3")
            .setSchemaUrl("http://schema.org")
            .build();
  }

  @Test
  void logRecordBuilder() {
    assertThat(logger.getClass().getName()).doesNotContain("Incubator");

    SpanContext spanContext =
        SpanContext.create(
            IdGenerator.random().generateTraceId(),
            IdGenerator.random().generateSpanId(),
            TraceFlags.getDefault(),
            TraceState.getDefault());

    logger
        .logRecordBuilder()
        .setEventName("eventName")
        .setTimestamp(1, SECONDS)
        .setTimestamp(Instant.now())
        .setContext(Context.current().with(Span.wrap(spanContext)))
        .setSeverity(Severity.DEBUG)
        .setSeverityText("debug")
        .setBody("body")
        .setAttribute(stringKey("key"), "value")
        .setAllAttributes(Attributes.builder().put("key", "value").build())
        .setException(new RuntimeException("exception"))
        .emit();

    await()
        .untilAsserted(
            () ->
                assertThat(testing.logRecords())
                    .satisfiesExactly(
                        logRecordData -> {
                          assertThat(logRecordData.getInstrumentationScopeInfo().getName())
                              .isEqualTo(instrumentationName);
                          assertThat(logRecordData.getEventName()).isEqualTo("eventName");
                          assertThat(logRecordData.getInstrumentationScopeInfo().getVersion())
                              .isEqualTo("1.2.3");
                          assertThat(logRecordData.getTimestampEpochNanos()).isGreaterThan(0);
                          assertThat(logRecordData.getSpanContext()).isEqualTo(spanContext);
                          assertThat(logRecordData.getSeverity()).isEqualTo(Severity.DEBUG);
                          assertThat(logRecordData.getSeverityText()).isEqualTo("debug");
                          assertThat(logRecordData.getBodyValue().getType())
                              .isEqualTo(ValueType.STRING);
                          assertThat(logRecordData.getBodyValue().getValue()).isEqualTo("body");
                          assertThat(logRecordData)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(stringKey("key"), "value"),
                                  equalTo(EXCEPTION_MESSAGE, "exception"),
                                  equalTo(EXCEPTION_TYPE, RuntimeException.class.getName()),
                                  satisfies(
                                      EXCEPTION_STACKTRACE,
                                      AbstractCharSequenceAssert::isNotEmpty));
                        }));
  }
}

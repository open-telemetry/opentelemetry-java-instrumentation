/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_52.incubator.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.common.ValueType;
import io.opentelemetry.api.incubator.common.ExtendedAttributeKey;
import io.opentelemetry.api.incubator.common.ExtendedAttributes;
import io.opentelemetry.api.incubator.logs.ExtendedLogger;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.logs.data.internal.ExtendedLogRecordData;
import io.opentelemetry.sdk.trace.IdGenerator;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    assertThat(logger).isInstanceOf(ExtendedLogger.class);

    SpanContext spanContext =
        SpanContext.create(
            IdGenerator.random().generateTraceId(),
            IdGenerator.random().generateSpanId(),
            TraceFlags.getDefault(),
            TraceState.getDefault());

    ((ExtendedLogger) logger)
        .logRecordBuilder()
        .setEventName("eventName")
        .setTimestamp(1, TimeUnit.SECONDS)
        .setTimestamp(Instant.now())
        .setContext(Context.current().with(Span.wrap(spanContext)))
        .setSeverity(Severity.DEBUG)
        .setSeverityText("debug")
        .setBody("body")
        .setAttribute(AttributeKey.stringKey("key1"), "value")
        .setAttribute(ExtendedAttributeKey.stringKey("key2"), "value")
        .setAllAttributes(Attributes.builder().put("key3", "value").build())
        .setAllAttributes(ExtendedAttributes.builder().put("key4", "value").build())
        .setAttribute(
            ExtendedAttributeKey.extendedAttributesKey("key5"),
            ExtendedAttributes.builder().put("key6", "value").build())
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
                          assertThat(
                                  ((ExtendedLogRecordData) logRecordData).getExtendedAttributes())
                              .isEqualTo(
                                  ExtendedAttributes.builder()
                                      .put("key1", "value")
                                      .put("key2", "value")
                                      .put("key3", "value")
                                      .put("key4", "value")
                                      .put(
                                          "key5",
                                          ExtendedAttributes.builder().put("key6", "value").build())
                                      .build());
                        }));
  }

  private static Stream<Arguments> bodyValues() {
    return Stream.of(
        Arguments.of(Value.of("hello")),
        Arguments.of(Value.of(42)),
        Arguments.of(Value.of(42.42)),
        Arguments.of(Value.of(true)),
        Arguments.of(Value.of(new byte[] {4, 2})),
        Arguments.of(Value.of(Value.of("hello"), Value.of(42))),
        Arguments.of(Value.of(KeyValue.of("key", Value.of(42)))));
  }

  @ParameterizedTest
  @MethodSource("bodyValues")
  void logBodyValue() {
    Value<?> value = Value.of(42);
    logger.logRecordBuilder().setBody(value).emit();

    await()
        .untilAsserted(
            () ->
                assertThat(testing.logRecords())
                    .satisfiesExactly(
                        logRecordData -> {
                          assertThat(logRecordData.getBodyValue().getType())
                              .isEqualTo(value.getType());
                          assertThat(logRecordData.getBodyValue().getValue())
                              .isEqualTo(value.getValue());
                        }));
  }

  @Test
  void logNullBody() {
    Value<?> value = null;
    logger.logRecordBuilder().setBody(value).emit();

    await()
        .untilAsserted(
            () ->
                assertThat(testing.logRecords())
                    .satisfiesExactly(
                        logRecordData -> assertThat(logRecordData.getBodyValue()).isNull()));
  }
}

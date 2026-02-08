/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_59;

import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleArrayKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.common.AttributeKey.valueKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Tests for VALUE attribute bridging from application API to agent API. */
class ValueAttributeTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void valueKeyHasCorrectType() {
    AttributeKey<Value<?>> key = valueKey("test-key");
    assertThat(key).isNotNull();
    assertThat(key.getKey()).isEqualTo("test-key");
    assertThat(key.getType()).isEqualTo(AttributeType.VALUE);
  }

  @Test
  void stringValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), Value.of("test"));
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(stringKey("key"), "test"))));
  }

  @Test
  void longValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), Value.of(123L));
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(longKey("key"), 123L))));
  }

  @Test
  void doubleValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), Value.of(1.23));
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(doubleKey("key"), 1.23))));
  }

  @Test
  void booleanValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), Value.of(true));
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(booleanKey("key"), true))));
  }

  @Test
  void stringArrayValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), Value.of(Arrays.asList(Value.of("a"), Value.of("b"))));
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringArrayKey("key"), Arrays.asList("a", "b")))));
  }

  @Test
  void longArrayValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), Value.of(Arrays.asList(Value.of(1L), Value.of(2L))));
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(
                            equalTo(longArrayKey("key"), Arrays.asList(1L, 2L)))));
  }

  @Test
  void doubleArrayValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), Value.of(Arrays.asList(Value.of(1.1), Value.of(2.2))));
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(
                            equalTo(doubleArrayKey("key"), Arrays.asList(1.1, 2.2)))));
  }

  @Test
  void booleanArrayValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(
        valueKey("key"), Value.of(Arrays.asList(Value.of(true), Value.of(false))));
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(
                            equalTo(booleanArrayKey("key"), Arrays.asList(true, false)))));
  }

  @Test
  void bytesValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Value<?> value = Value.of(new byte[] {1, 2, 3});
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), value);
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void keyValueListValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Value<?> value =
        Value.of(KeyValue.of("key1", Value.of("value1")), KeyValue.of("key2", Value.of(123L)));
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), value);
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void nestedArrayValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Value<?> value =
        Value.of(
            Arrays.asList(
                Value.of(Arrays.asList(Value.of("a"), Value.of("b"))),
                Value.of(Arrays.asList(Value.of("c"), Value.of("d")))));
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), value);
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void heterogeneousArrayValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Value<?> value = Value.of(Value.of("string"), Value.of(42L), Value.of(true));
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), value);
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void emptyValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("emptyValue"), Value.empty());
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(
                            equalTo(valueKey("emptyValue"), Value.empty()))));
  }

  @Test
  void nullValueIsDropped() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("nullValue"), null);
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-span").hasAttributesSatisfyingExactly()));
  }

  @Test
  void emptyArrayValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Value<?> value = Value.of(Collections.<Value<?>>emptyList());
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), value);
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void nestedKeyValueListValue() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Value<?> value =
        Value.of(KeyValue.of("outer", Value.of(KeyValue.of("inner", Value.of("deep")))));
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("key"), value);
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void multipleValueAttributes() {
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Value<?> bytesValue = Value.of(new byte[] {0});
    Span testSpan = tracer.spanBuilder("test-span").startSpan();
    testSpan.setAttribute(valueKey("string"), Value.of("hello"));
    testSpan.setAttribute(valueKey("long"), Value.of(42L));
    testSpan.setAttribute(valueKey("bytes"), bytesValue);
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("string"), "hello"),
                            equalTo(longKey("long"), 42L),
                            equalTo(valueKey("bytes"), bytesValue))));
  }
}

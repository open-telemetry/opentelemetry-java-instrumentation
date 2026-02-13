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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.Span;
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
    testing.runWithSpan(
        "test-span", () -> Span.current().setAttribute(valueKey("key"), Value.of("test")));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(stringKey("key"), "test"))));
  }

  @Test
  void longValue() {
    testing.runWithSpan(
        "test-span", () -> Span.current().setAttribute(valueKey("key"), Value.of(123L)));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(longKey("key"), 123L))));
  }

  @Test
  void doubleValue() {
    testing.runWithSpan(
        "test-span", () -> Span.current().setAttribute(valueKey("key"), Value.of(1.23)));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(doubleKey("key"), 1.23))));
  }

  @Test
  void booleanValue() {
    testing.runWithSpan(
        "test-span", () -> Span.current().setAttribute(valueKey("key"), Value.of(true)));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(booleanKey("key"), true))));
  }

  @Test
  void stringArrayValue() {
    testing.runWithSpan(
        "test-span",
        () ->
            Span.current()
                .setAttribute(
                    valueKey("key"), Value.of(Arrays.asList(Value.of("a"), Value.of("b")))));

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
    testing.runWithSpan(
        "test-span",
        () ->
            Span.current()
                .setAttribute(
                    valueKey("key"), Value.of(Arrays.asList(Value.of(1L), Value.of(2L)))));

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
    testing.runWithSpan(
        "test-span",
        () ->
            Span.current()
                .setAttribute(
                    valueKey("key"), Value.of(Arrays.asList(Value.of(1.1), Value.of(2.2)))));

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
    testing.runWithSpan(
        "test-span",
        () ->
            Span.current()
                .setAttribute(
                    valueKey("key"), Value.of(Arrays.asList(Value.of(true), Value.of(false)))));

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
    Value<?> value = Value.of(new byte[] {1, 2, 3});
    testing.runWithSpan("test-span", () -> Span.current().setAttribute(valueKey("key"), value));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void keyValueListValue() {
    Value<?> value =
        Value.of(KeyValue.of("key1", Value.of("value1")), KeyValue.of("key2", Value.of(123L)));
    testing.runWithSpan("test-span", () -> Span.current().setAttribute(valueKey("key"), value));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void nestedArrayValue() {
    Value<?> value =
        Value.of(
            Arrays.asList(
                Value.of(Arrays.asList(Value.of("a"), Value.of("b"))),
                Value.of(Arrays.asList(Value.of("c"), Value.of("d")))));
    testing.runWithSpan("test-span", () -> Span.current().setAttribute(valueKey("key"), value));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void heterogeneousArrayValue() {
    Value<?> value = Value.of(Value.of("string"), Value.of(42L), Value.of(true));
    testing.runWithSpan("test-span", () -> Span.current().setAttribute(valueKey("key"), value));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void emptyValue() {
    testing.runWithSpan(
        "test-span", () -> Span.current().setAttribute(valueKey("emptyValue"), Value.empty()));

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
    testing.runWithSpan(
        "test-span", () -> Span.current().setAttribute(valueKey("nullValue"), null));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-span").hasAttributesSatisfyingExactly()));
  }

  @Test
  void emptyArrayValue() {
    Value<?> value = Value.of(Collections.<Value<?>>emptyList());
    testing.runWithSpan("test-span", () -> Span.current().setAttribute(valueKey("key"), value));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void nestedKeyValueListValue() {
    Value<?> value =
        Value.of(KeyValue.of("outer", Value.of(KeyValue.of("inner", Value.of("deep")))));
    testing.runWithSpan("test-span", () -> Span.current().setAttribute(valueKey("key"), value));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-span")
                        .hasAttributesSatisfyingExactly(equalTo(valueKey("key"), value))));
  }

  @Test
  void multipleValueAttributes() {
    Value<?> bytesValue = Value.of(new byte[] {0});
    testing.runWithSpan(
        "test-span",
        () -> {
          Span.current().setAttribute(valueKey("string"), Value.of("hello"));
          Span.current().setAttribute(valueKey("long"), Value.of(42L));
          Span.current().setAttribute(valueKey("bytes"), bytesValue);
        });

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

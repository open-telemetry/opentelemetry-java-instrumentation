/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.StatusDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// This test has "test/config/nocode.yml" applied to it by the gradle environment setting
class NocodeInstrumentationTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testBasicMethod() {
    new SampleClass().doSomething();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("name")
                        .hasKind(SpanKind.INTERNAL)
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfying(
                            equalTo(AttributeKey.doubleKey("getFloat"), 7.0),
                            equalTo(AttributeKey.doubleKey("getDouble"), 8.0),
                            equalTo(AttributeKey.longKey("getLong"), 9),
                            equalTo(AttributeKey.longKey("getShort"), 10),
                            equalTo(AttributeKey.longKey("getByte"), 11),
                            equalTo(AttributeKey.booleanKey("map.isEmpty"), false),
                            equalTo(AttributeKey.longKey("map.size"), 2),
                            equalTo(AttributeKey.stringKey("details"), "details"))));
  }

  @Test
  void testRuleWithManyInvalidFields() {
    new SampleClass().doInvalidRule();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SampleClass.doInvalidRule")
                        .hasKind(SpanKind.INTERNAL)
                        .hasStatus(StatusData.unset())
                        .hasTotalAttributeCount(
                            2))); // two code. attribute but nothing from the invalid rule
  }

  @Test
  void testThrowException() {
    try {
      new SampleClass().throwException(5);
    } catch (UnsupportedOperationException expected) {
      // nop
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SampleClass.throwException")
                        .hasKind(SpanKind.SERVER)
                        .hasEventsSatisfyingExactly(event -> event.hasName("exception"))
                        .hasStatusSatisfying(StatusDataAssert::isError)
                        .hasAttributesSatisfying(equalTo(AttributeKey.stringKey("five"), "5"))));
  }

  @Test
  void testEchoTrueIsError() {
    new SampleClass().echo(true);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SampleClass.echo")
                        .hasStatusSatisfying(StatusDataAssert::isError)));
  }

  @Test
  void testEchoFalseIsOk() {
    new SampleClass().echo(false);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SampleClass.echo").hasStatusSatisfying(StatusDataAssert::isOk)));
  }

  public static class SampleClass {
    public String getName() {
      return "name";
    }

    public String getDetails() {
      return "details";
    }

    public float getFloat() {
      return 7.0f;
    }

    public double getDouble() {
      return 8.0;
    }

    public long getLong() {
      return 9L;
    }

    public short getShort() {
      return 10;
    }

    public byte getByte() {
      return 11;
    }

    public Map<String, String> getMap() {
      HashMap<String, String> answer = new HashMap<>();
      answer.put("key", "value");
      answer.put("key2", "value2");
      return answer;
    }

    public void throwException(int parameter) {
      throw new UnsupportedOperationException("oh no");
    }

    public void doSomething() {}

    public void doInvalidRule() {}

    public boolean echo(boolean b) {
      return b;
    }
  }
}

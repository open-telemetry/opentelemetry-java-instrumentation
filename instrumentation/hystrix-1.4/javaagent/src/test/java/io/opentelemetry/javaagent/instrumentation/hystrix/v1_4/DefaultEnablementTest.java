/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix.v1_4;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DefaultEnablementTest {

  private static final boolean V3_PREVIEW =
      Boolean.getBoolean("otel.instrumentation.common.v3-preview");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void defaultEnablement() {
    class TestCommand extends HystrixCommand<String> {
      TestCommand() {
        super(setter());
      }

      @Override
      protected String run() {
        return "Hello!";
      }
    }

    String result = testing.runWithSpan("parent", () -> new TestCommand().execute());
    assertThat(result).isEqualTo("Hello!");

    if (V3_PREVIEW) {
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span -> span.hasName("ExampleGroup.TestCommand.execute")));
    }
  }

  private static HystrixCommand.Setter setter() {
    HystrixCommand.Setter setter =
        HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
    setter.andCommandPropertiesDefaults(
        HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(10_000));
    return setter;
  }
}

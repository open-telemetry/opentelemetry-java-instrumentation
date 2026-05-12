/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static io.opentelemetry.api.common.Attributes.empty;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ThreadDetailsInstrumenterCustomizerProviderTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @AfterEach
  void tearDown() {
    AgentDistributionConfig.resetForTest();
  }

  public static Stream<Arguments> allEnabledAndDisabledValues() {
    return Stream.of(
        Arguments.of(
            true,
            (Consumer<SpanDataAssert>)
                span ->
                    span.hasAttributesSatisfying(
                        satisfies(THREAD_ID, n -> n.isEqualTo(Thread.currentThread().getId())),
                        satisfies(
                            THREAD_NAME, n -> n.isEqualTo(Thread.currentThread().getName())))),
        Arguments.of(false, (Consumer<SpanDataAssert>) span -> span.hasAttributes(empty())));
  }

  public static Stream<Arguments> implicitDefaultValues() {
    return Stream.of(
        Arguments.of(
            false,
            (Consumer<SpanDataAssert>)
                span ->
                    span.hasAttributesSatisfying(
                        satisfies(THREAD_ID, n -> n.isEqualTo(Thread.currentThread().getId())),
                        satisfies(
                            THREAD_NAME, n -> n.isEqualTo(Thread.currentThread().getName())))),
        Arguments.of(true, (Consumer<SpanDataAssert>) span -> span.hasAttributes(empty())));
  }

  @ParameterizedTest(name = "enabled={0}")
  @MethodSource("allEnabledAndDisabledValues")
  void enabled(boolean enabled, Consumer<SpanDataAssert> spanAttributesConsumer) {
    AgentDistributionConfig.set(
        AgentDistributionConfig.fromConfigProperties(
            DefaultConfigProperties.createFromMap(
                singletonMap("otel.javaagent.add-thread-details", String.valueOf(enabled)))));
    assertInstrumenterAttributes(spanAttributesConsumer);
  }

  @ParameterizedTest(name = "v3Preview={0}")
  @MethodSource("implicitDefaultValues")
  void implicitDefault(boolean v3Preview, Consumer<SpanDataAssert> spanAttributesConsumer) {
    Map<String, String> config = new HashMap<>();
    config.put("otel.instrumentation.common.v3-preview", String.valueOf(v3Preview));
    AgentDistributionConfig.set(
        AgentDistributionConfig.fromConfigProperties(
            DefaultConfigProperties.createFromMap(config)));

    assertInstrumenterAttributes(spanAttributesConsumer);
  }

  private static void assertInstrumenterAttributes(
      Consumer<SpanDataAssert> spanAttributesConsumer) {
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                otelTesting.getOpenTelemetry(), "test", name -> "span")
            .buildInstrumenter();

    Context context = instrumenter.start(Context.root(), emptyMap());
    instrumenter.end(context, emptyMap(), emptyMap(), null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace -> trace.hasSpansSatisfyingExactly(spanAttributesConsumer));
  }
}

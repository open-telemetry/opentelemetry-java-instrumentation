/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationBuilder;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.InstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AddThreadDetailsTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  public static Stream<Arguments> allEnabledAndDisabledValues() {
    return Stream.of(
        Arguments.of(
            true,
            (Consumer<SpanDataAssert>)
                span ->
                    span.hasAttributesSatisfying(
                        satisfies(ThreadIncubatingAttributes.THREAD_ID, n -> n.isNotNull()),
                        satisfies(ThreadIncubatingAttributes.THREAD_NAME, n -> n.isNotBlank()))),
        Arguments.of(
            false,
            (Consumer<SpanDataAssert>)
                span ->
                    span.hasAttributesSatisfying(
                        satisfies(ThreadIncubatingAttributes.THREAD_ID, n -> n.isNull()),
                        satisfies(ThreadIncubatingAttributes.THREAD_NAME, n -> n.isNull()))));
  }

  @ParameterizedTest(name = "enabled={0}")
  @MethodSource("allEnabledAndDisabledValues")
  void enabled(boolean enabled, Consumer<SpanDataAssert> spanAttributesConsumer)
      throws NoSuchFieldException, IllegalAccessException {
    OpenTelemetry openTelemetry = DeclarativeConfiguration.create(model(enabled));
    Instrumenter<Map<String, String>, Map<String, String>> instrumenter =
        Instrumenter.<Map<String, String>, Map<String, String>>builder(
                openTelemetry, "test", name -> "span")
            .buildInstrumenter();

    // OpenTelemetryExtension doesn't allow passing a custom OpenTelemetry instance
    Field field = Instrumenter.class.getDeclaredField("tracer");
    field.setAccessible(true);
    field.set(instrumenter, otelTesting.getOpenTelemetry().getTracer("test"));

    Context context = instrumenter.start(Context.root(), emptyMap());
    instrumenter.end(context, emptyMap(), emptyMap(), null);

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace -> trace.hasSpansSatisfyingExactly(spanAttributesConsumer));
  }

  private static OpenTelemetryConfigurationModel model(boolean enabled) {
    return new DeclarativeConfigurationBuilder()
        .customizeModel(
            new OpenTelemetryConfigurationModel()
                .withFileFormat("1.0-rc.1")
                .withInstrumentationDevelopment(
                    new InstrumentationModel()
                        .withJava(
                            new ExperimentalLanguageSpecificInstrumentationModel()
                                .withAdditionalProperty(
                                    "thread_details", singletonMap("enabled", enabled)))));
  }
}

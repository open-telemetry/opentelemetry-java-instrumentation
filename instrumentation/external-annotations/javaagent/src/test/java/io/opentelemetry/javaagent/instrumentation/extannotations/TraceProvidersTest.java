/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** This test verifies that Otel supports various 3rd-party trace annotations */
class TraceProvidersTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @MethodSource("provideArguments")
  void testShouldSupportProvider(String provider)
      throws ClassNotFoundException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException,
          InvocationTargetException {

    Class<?> cls =
        Class.forName("io.opentelemetry.javaagent.instrumentation.extannotations.SayTracedHello");
    Method method = cls.getMethod(provider.toLowerCase(Locale.ROOT));
    Object obj = cls.getDeclaredConstructor().newInstance();
    method.invoke(obj);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SayTracedHello." + provider.toLowerCase(Locale.ROOT))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                SemanticAttributes.CODE_NAMESPACE, SayTracedHello.class.getName()),
                            equalTo(
                                SemanticAttributes.CODE_FUNCTION,
                                provider.toLowerCase(Locale.ROOT)),
                            equalTo(stringKey("providerAttr"), provider))));
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of("AppOptics"),
        Arguments.of("Datadog"),
        Arguments.of("Dropwizard"),
        Arguments.of("KamonOld"),
        Arguments.of("KamonNew"),
        Arguments.of("NewRelic"),
        Arguments.of("SignalFx"),
        Arguments.of("Sleuth"),
        Arguments.of("Tracelytics"));
  }
}

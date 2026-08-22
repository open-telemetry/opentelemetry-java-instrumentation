/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.AgentClassLoaderAccess;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CamelProcessMetricsTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.camel-2.20";
  private static final String INSTRUMENTATION_PACKAGE =
      "io.opentelemetry.javaagent.instrumentation.camel.v2_20.";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  void setUp() throws Exception {
    assumeTrue(emitStableMessagingSemconv());
    assumeFalse(v3Preview());
    DefaultCamelContext context = new DefaultCamelContext();
    try {
      context.start();
    } finally {
      context.stop();
    }
  }

  @Test
  void explicitSuppressionDoesNotRecordFallbackMetrics() throws ReflectiveOperationException {
    Exchange exchange = new DefaultExchange(new DefaultCamelContext());
    Endpoint endpoint = mock(Endpoint.class);
    when(endpoint.getEndpointUri()).thenReturn("jms:queue:testQueue");
    Route route = mock(Route.class);
    when(route.getEndpoint()).thenReturn(endpoint);

    Class<?> contextClass =
        Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.Context");
    AtomicReference<Object> parentContext = new AtomicReference<>();
    Runnable captureContext =
        () -> {
          try {
            parentContext.set(contextClass.getMethod("current").invoke(null));
          } catch (ReflectiveOperationException e) {
            throw new LinkageError(e.getMessage(), e);
          }
        };
    Class<?> instrumentationUtilClass =
        Class.forName(
            "io.opentelemetry.javaagent.shaded.io.opentelemetry.api.impl.InstrumentationUtil");
    instrumentationUtilClass
        .getMethod("suppressInstrumentation", Runnable.class)
        .invoke(null, captureContext);

    Class<?> singletonsClass = camelHelperClass("CamelSingletons");
    Object decorator =
        invokeStatic(
            singletonsClass, "getSpanDecorator", new Class<?>[] {Endpoint.class}, endpoint);
    Class<?> decoratorClass = camelHelperClass("SpanDecorator");
    Class<?> routePolicyClass = camelHelperClass("CamelRoutePolicy");
    Object context =
        invokeStatic(
            routePolicyClass,
            "spanOnExchangeBegin",
            new Class<?>[] {Route.class, Exchange.class, decoratorClass, contextClass},
            route,
            exchange,
            decorator,
            parentContext.get());
    assertThat(context).isNull();

    Class<?> processMetricsClass = camelHelperClass("CamelProcessMetrics");
    invokeStatic(
        processMetricsClass, "end", new Class<?>[] {Route.class, Exchange.class}, route, exchange);
    assertThat(testing.metrics())
        .filteredOn(
            metric -> INSTRUMENTATION_NAME.equals(metric.getInstrumentationScopeInfo().getName()))
        .isEmpty();
  }

  @ParameterizedTest
  @MethodSource("partialOwnership")
  void recordsOnlyUnclaimedMetric(String markerMethod, String expectedMetric)
      throws ReflectiveOperationException {
    Exchange exchange = new DefaultExchange(new DefaultCamelContext());
    Endpoint endpoint = mock(Endpoint.class);
    when(endpoint.getEndpointUri()).thenReturn("jms:queue:testQueue");
    // Javaagent helpers are loaded with shaded API types, so drive this helper through reflection.
    Class<?> contextClass =
        Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.Context");
    Object parentContext = contextClass.getMethod("root").invoke(null);
    Class<?> metricsStateClass =
        Class.forName(
            "io.opentelemetry.javaagent.shaded.instrumentation.api.incubator.semconv.messaging.internal.MessagingMetricsState");
    parentContext =
        metricsStateClass.getMethod(markerMethod, contextClass).invoke(null, parentContext);

    Class<?> singletonsClass = camelHelperClass("CamelSingletons");
    Object decorator =
        invokeStatic(
            singletonsClass, "getSpanDecorator", new Class<?>[] {Endpoint.class}, endpoint);
    Class<?> decoratorClass = camelHelperClass("SpanDecorator");
    Class<?> directionClass = camelHelperClass("CamelDirection");
    Class<?> spanKindClass =
        Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.api.trace.SpanKind");
    Class<?> requestClass = camelHelperClass("CamelRequest");
    Object request =
        invokeStatic(
            requestClass,
            "create",
            new Class<?>[] {
              decoratorClass, Exchange.class, Endpoint.class, directionClass, spanKindClass
            },
            decorator,
            exchange,
            endpoint,
            enumConstant(directionClass, "INBOUND"),
            enumConstant(spanKindClass, "CONSUMER"));
    Route route = mock(Route.class);

    Class<?> processMetricsClass = camelHelperClass("CamelProcessMetrics");
    invokeStatic(
        processMetricsClass,
        "start",
        new Class<?>[] {Route.class, contextClass, requestClass},
        route,
        parentContext,
        request);
    invokeStatic(
        processMetricsClass, "end", new Class<?>[] {Route.class, Exchange.class}, route, exchange);

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, expectedMetric, metrics -> metrics.hasSize(1));
    assertThat(testing.metrics())
        .filteredOn(
            metric -> INSTRUMENTATION_NAME.equals(metric.getInstrumentationScopeInfo().getName()))
        .extracting(MetricData::getName)
        .containsExactly(expectedMetric);
  }

  private static Stream<Arguments> partialOwnership() {
    return Stream.of(
        argumentSet(
            "consumed messages already owned",
            "markConsumedMessages",
            "messaging.process.duration"),
        argumentSet(
            "process duration already owned",
            "markProcessDuration",
            "messaging.client.consumed.messages"));
  }

  private static Class<?> camelHelperClass(String simpleName) throws ReflectiveOperationException {
    String className = INSTRUMENTATION_PACKAGE + simpleName;
    if (!Boolean.getBoolean("otel.javaagent.experimental.indy")) {
      return Class.forName(className);
    }

    Class<?> registryClass =
        AgentClassLoaderAccess.loadClass(
            "io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyModuleRegistry");
    ClassLoader moduleClassLoader =
        (ClassLoader)
            registryClass
                .getMethod("getInstrumentationClassLoader", String.class, ClassLoader.class)
                .invoke(
                    null,
                    INSTRUMENTATION_PACKAGE + "ApacheCamelInstrumentationModule",
                    DefaultCamelContext.class.getClassLoader());
    return moduleClassLoader.loadClass(className);
  }

  private static Object invokeStatic(
      Class<?> type, String methodName, Class<?>[] parameterTypes, Object... arguments)
      throws ReflectiveOperationException {
    Method method = type.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(null, arguments);
  }

  private static Object enumConstant(Class<?> type, String name) {
    return Arrays.stream(type.getEnumConstants())
        .filter(value -> ((Enum<?>) value).name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown enum constant: " + name));
  }
}

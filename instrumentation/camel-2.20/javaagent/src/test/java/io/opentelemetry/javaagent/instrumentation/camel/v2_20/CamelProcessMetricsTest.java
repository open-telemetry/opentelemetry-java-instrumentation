/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.ExperimentalTest.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.StatusCode;
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
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
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
    DefaultCamelContext context = new DefaultCamelContext();
    try {
      context.start();
    } finally {
      context.stop();
    }
  }

  @ParameterizedTest
  @MethodSource("exchangeOutcomes")
  void classifiesMessagingExchange(String direction, String outcome, String errorType)
      throws ReflectiveOperationException {
    Exchange exchange = new DefaultExchange(new DefaultCamelContext());
    Endpoint endpoint = mock(Endpoint.class);
    when(endpoint.getEndpointUri()).thenReturn("jms:queue:testQueue");
    Route route = mock(Route.class);
    when(route.getEndpoint()).thenReturn(endpoint);
    Class<?> contextClass =
        Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.Context");
    boolean send = direction.equals("send");
    Class<?> notifier = camelHelperClass("CamelEventNotifier");
    if (send) {
      invokeStatic(
          notifier,
          "onExchangeSending",
          new Class<?>[] {ExchangeSendingEvent.class},
          new ExchangeSendingEvent(exchange, endpoint));
    } else {
      Object decorator =
          invokeStatic(
              camelHelperClass("CamelSingletons"),
              "getSpanDecorator",
              new Class<?>[] {Endpoint.class},
              endpoint);
      invokeStatic(
          camelHelperClass("CamelRoutePolicy"),
          "spanOnExchangeBegin",
          new Class<?>[] {
            Route.class, Exchange.class, camelHelperClass("SpanDecorator"), contextClass
          },
          route,
          exchange,
          decorator,
          contextClass.getMethod("root").invoke(null));
    }
    completeExchange(exchange, outcome);
    if (send) {
      invokeStatic(
          notifier,
          "onExchangeSent",
          new Class<?>[] {ExchangeSentEvent.class},
          new ExchangeSentEvent(exchange, endpoint, 1));
    } else {
      invokeStatic(
          camelHelperClass("ActiveContextManager"),
          "deactivate",
          new Class<?>[] {Exchange.class},
          exchange);
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(direction + " testQueue")
                        .hasStatusSatisfying(
                            status ->
                                status.hasCode(
                                    errorType == null ? StatusCode.UNSET : StatusCode.ERROR))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("camel.uri"), experimental("jms:queue:testQueue")),
                            equalTo(MESSAGING_SYSTEM, "jms"),
                            equalTo(MESSAGING_DESTINATION_NAME, "testQueue"),
                            equalTo(MESSAGING_OPERATION_NAME, direction),
                            equalTo(MESSAGING_OPERATION_TYPE, direction),
                            equalTo(ERROR_TYPE, errorType))));
    if (send) {
      CamelMessagingMetricsAssertions.assertSendMetrics(testing, "jms", "testQueue", errorType);
    } else {
      CamelMessagingMetricsAssertions.assertProcessMetrics(testing, "jms", "testQueue", errorType);
    }
  }

  private static Stream<Arguments> exchangeOutcomes() {
    return Stream.of("send", "process")
        .flatMap(
            direction ->
                Stream.of(
                    Arguments.of(direction, "fault", "_OTHER"),
                    Arguments.of(direction, "exception", IllegalStateException.class.getName()),
                    Arguments.of(
                        direction, "faultAndException", IllegalStateException.class.getName()),
                    Arguments.of(direction, "handled", null)));
  }

  private static void completeExchange(Exchange exchange, String outcome) {
    if (outcome.equals("fault") || outcome.equals("faultAndException")) {
      exchange.getOut().setFault(true);
    }
    if (!outcome.equals("fault")) {
      exchange.setException(new IllegalStateException("failed"));
    }
    if (outcome.equals("handled")) {
      exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exchange.getException());
      exchange.setException(null);
      exchange.setProperty(Exchange.FAILURE_HANDLED, true);
    }
    assertThat(exchange.isFailed()).isEqualTo(!outcome.equals("handled"));
    if (outcome.equals("fault")) {
      assertThat(exchange.getException()).isNull();
    }
  }

  @ParameterizedTest
  @MethodSource("processOutcomes")
  void classifiesSuppressedRouteWithoutChangingOwner(String outcome, String errorType)
      throws ReflectiveOperationException {
    testing.runWithSpan(
        "owner",
        () -> {
          Exchange exchange = new DefaultExchange(new DefaultCamelContext());
          Endpoint endpoint = mock(Endpoint.class);
          when(endpoint.getEndpointUri()).thenReturn("jms:queue:testQueue");
          Route route = mock(Route.class);
          when(route.getEndpoint()).thenReturn(endpoint);
          Class<?> contextClass =
              Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.Context");
          Object parentContext = contextClass.getMethod("current").invoke(null);
          Class<?> spanClass =
              Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.api.trace.Span");
          Object owner =
              spanClass.getMethod("fromContext", contextClass).invoke(null, parentContext);
          Class<?> spanKeyClass =
              Class.forName(
                  "io.opentelemetry.javaagent.shaded.instrumentation.api.internal.SpanKey");
          parentContext =
              spanKeyClass
                  .getMethod("storeInContext", contextClass, spanClass)
                  .invoke(
                      spanKeyClass.getField("CONSUMER_PROCESS").get(null), parentContext, owner);
          Object decorator =
              invokeStatic(
                  camelHelperClass("CamelSingletons"),
                  "getSpanDecorator",
                  new Class<?>[] {Endpoint.class},
                  endpoint);
          Object context =
              invokeStatic(
                  camelHelperClass("CamelRoutePolicy"),
                  "spanOnExchangeBegin",
                  new Class<?>[] {
                    Route.class, Exchange.class, camelHelperClass("SpanDecorator"), contextClass
                  },
                  route,
                  exchange,
                  decorator,
                  parentContext);
          assertThat(context).isNull();
          completeExchange(exchange, outcome);
          invokeStatic(
              camelHelperClass("CamelProcessMetrics"),
              "end",
              new Class<?>[] {Route.class, Exchange.class},
              route,
              exchange);
        });
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("owner")
                        .hasStatusSatisfying(status -> status.hasCode(StatusCode.UNSET))
                        .hasTotalAttributeCount(0)));
    CamelMessagingMetricsAssertions.assertProcessMetrics(testing, "jms", "testQueue", errorType);
  }

  private static Stream<Arguments> processOutcomes() {
    return Stream.of(
        Arguments.of("fault", "_OTHER"),
        Arguments.of("exception", IllegalStateException.class.getName()),
        Arguments.of("faultAndException", IllegalStateException.class.getName()),
        Arguments.of("handled", null));
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
  @MethodSource("signalsAlreadyPresent")
  void recordsOnlyAbsentMetric(String operation, String signal, String expectedMetric)
      throws ReflectiveOperationException {
    Exchange exchange = new DefaultExchange(new DefaultCamelContext());
    Endpoint endpoint = mock(Endpoint.class);
    when(endpoint.getEndpointUri()).thenReturn("jms:queue:testQueue");
    // Javaagent helpers are loaded with shaded API types, so drive this helper through reflection.
    Class<?> contextClass =
        Class.forName("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.Context");
    Object parentContext = contextClass.getMethod("root").invoke(null);
    Class<?> telemetryStateClass = shadedApiClass("messaging.internal.MessagingTelemetryState");
    Class<?> operationTypeClass = shadedApiClass("messaging.MessagingOperationType");
    Class<?> signalClass = shadedApiClass("messaging.internal.MessagingTelemetrySignal");
    parentContext =
        telemetryStateClass
            .getMethod("add", contextClass, operationTypeClass, signalClass)
            .invoke(
                null,
                parentContext,
                enumConstant(operationTypeClass, operation),
                enumConstant(signalClass, signal));

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

  private static Stream<Arguments> signalsAlreadyPresent() {
    return Stream.of(
        argumentSet(
            "consumed messages already present",
            "RECEIVE",
            "CONSUMED_MESSAGES",
            "messaging.process.duration"),
        argumentSet(
            "process duration already present",
            "PROCESS",
            "PROCESS_DURATION",
            "messaging.client.consumed.messages"));
  }

  private static Class<?> shadedApiClass(String name) throws ReflectiveOperationException {
    return Class.forName(
        "io.opentelemetry.javaagent.shaded.instrumentation.api.incubator.semconv." + name);
  }

  private static Class<?> camelHelperClass(String simpleName) throws ReflectiveOperationException {
    String className = INSTRUMENTATION_PACKAGE + simpleName;
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

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi;

import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.opentelemetry.semconv.SchemaUrls;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import rmi.app.ChildValue;
import rmi.app.Greeter;
import rmi.app.NumericGreeter;
import rmi.app.ParentGreeter;
import rmi.app.Server;

class RmiTest {

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension autoCleanup = AutoCleanupExtension.create();

  private static Registry serverRegistry;
  private static Registry clientRegistry;

  @BeforeAll
  static void setUp() throws Exception {
    int registryPort = PortUtils.findOpenPort();
    serverRegistry = LocateRegistry.createRegistry(registryPort);
    clientRegistry = LocateRegistry.getRegistry("localhost", registryPort);
    autoCleanup.deferAfterAll(() -> UnicastRemoteObject.unexportObject(serverRegistry, true));
  }

  @Test
  void clientCallCreatesSpans() throws Exception {
    Server server = new Server();
    serverRegistry.rebind(Server.RMI_ID, server);
    autoCleanup.deferCleanup(() -> serverRegistry.unbind(Server.RMI_ID));

    String response =
        testing.runWithSpan(
            "parent",
            () -> {
              Greeter client = (Greeter) clientRegistry.lookup(Server.RMI_ID);
              return client.hello("you");
            });

    assertThat(response).contains("Hello you");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("rmi.app.Greeter/hello")
                        .hasKind(SpanKind.CLIENT)
.satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM_NAME, "java_rmi"),
                            equalTo(
                                RPC_METHOD,
                                "rmi.app.Greeter/hello")),
                span ->
                    span.hasName(
                            "rmi.app.Greeter/hello")
                        .hasKind(SpanKind.SERVER)
.satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM_NAME, "java_rmi"),
                            equalTo(
                                RPC_METHOD,
                                "rmi.app.Greeter/hello"))));

    assertDurationMetrics("rmi.app.Greeter/hello", "rmi.app.Greeter/hello", null);
  }

  @Test
  @SuppressWarnings("ReturnValueIgnored")
  void serverBuiltinMethods() throws RemoteException {
    Server server = new Server();
    serverRegistry.rebind(Server.RMI_ID, server);
    autoCleanup.deferCleanup(() -> serverRegistry.unbind(Server.RMI_ID));

    server.equals(new Server());
    server.getRef();
    server.hashCode();
    server.toString();
    server.getClass();

    assertThat(testing.waitForTraces(0)).isEmpty();
  }

  @Test
  void serviceThrownException() throws RemoteException {
    Server server = new Server();
    serverRegistry.rebind(Server.RMI_ID, server);
    autoCleanup.deferCleanup(() -> serverRegistry.unbind(Server.RMI_ID));

    IllegalStateException thrown =
        catchThrowableOfType(
            IllegalStateException.class,
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      Greeter client = (Greeter) clientRegistry.lookup(Server.RMI_ID);
                      client.exceptional();
                    }));

    assertThat(thrown).isNotNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("rmi.app.Greeter/exceptional")
                        .hasKind(SpanKind.CLIENT)
.satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                        .hasParent(trace.getSpan(0))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            EXCEPTION_TYPE, thrown.getClass().getCanonicalName()),
                                        equalTo(EXCEPTION_MESSAGE, thrown.getMessage()),
                                        satisfies(EXCEPTION_STACKTRACE, val -> val.isNotNull())))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM_NAME, "java_rmi"),
                            equalTo(
                                RPC_METHOD,
                                "rmi.app.Greeter/exceptional"),
                            equalTo(
                                ERROR_TYPE,
                                thrown.getClass().getName())),
                span ->
                    span.hasName(
                            "rmi.app.Greeter/exceptional")
                        .hasKind(SpanKind.SERVER)
.satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            EXCEPTION_TYPE, thrown.getClass().getCanonicalName()),
                                        equalTo(EXCEPTION_MESSAGE, thrown.getMessage()),
                                        satisfies(EXCEPTION_STACKTRACE, val -> val.isNotNull())))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM_NAME, "java_rmi"),
                            equalTo(
                                RPC_METHOD,
                                "rmi.app.Greeter/exceptional"),
                            equalTo(
                                ERROR_TYPE,
                                thrown.getClass().getName()))));

    assertDurationMetrics(
        "rmi.app.Greeter/exceptional", "rmi.app.Greeter/exceptional", thrown.getClass().getName());
  }

  @ParameterizedTest
  @MethodSource("remoteMethods")
  void remoteInterfaceIdentity(
      Method method, Object argument, Object expectedResponse, String remoteService)
      throws Exception {
    Server server = new Server.Child();
    autoCleanup.deferCleanup(() -> UnicastRemoteObject.unexportObject(server, true));
    serverRegistry.rebind(Server.RMI_ID, server);
    autoCleanup.deferCleanup(() -> serverRegistry.unbind(Server.RMI_ID));

    Object response =
        testing.runWithSpan(
            "parent",
            () -> {
              Remote client = clientRegistry.lookup(Server.RMI_ID);
              return method.getParameterCount() == 0
                  ? method.invoke(client)
                  : method.invoke(client, argument);
            });
    assertThat(response).isEqualTo(expectedResponse);

    String methodName = method.getName();
    String clientService = method.getDeclaringClass().getName();
    String rpcMethod = remoteService == null ? null : remoteService + "/" + methodName;
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(clientService + "/" + methodName)
                        .hasKind(SpanKind.CLIENT)
.satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM_NAME, "java_rmi"),
                            equalTo(
                                RPC_METHOD,
                                clientService + "/" + methodName)),
                span ->
                    span.hasName(
                            (rpcMethod == null ? "java_rmi" : rpcMethod))
                        .hasKind(SpanKind.SERVER)
.satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM_NAME, "java_rmi"),
                            equalTo(
                                RPC_METHOD, rpcMethod))));

    assertDurationMetrics(clientService + "/" + methodName, rpcMethod, null);
  }

  private static void assertDurationMetrics(
      String clientMethod, String serverMethod, String errorType) {
    assertDurationMetric("rpc.client.call.duration", clientMethod, errorType);
    assertDurationMetric("rpc.server.call.duration", serverMethod, errorType);
  }

  private static void assertDurationMetric(String metricName, String rpcMethod, String errorType) {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.rmi",
        metricName,
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasName(metricName)
.satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0))
                        .hasUnit("s")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasBucketBoundaries(0.0001, 0.0005, 0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0, 10.0, 50.0, 100.0)
.hasAttributesSatisfyingExactly(
                                                equalTo(RPC_SYSTEM_NAME, "java_rmi"),
                                                equalTo(RPC_METHOD, rpcMethod),
                                                equalTo(ERROR_TYPE, errorType))))));
  }

  private static Stream<Arguments> remoteMethods() throws NoSuchMethodException {
    Stream<Arguments> childValueMethods =
        Arrays.stream(ChildValue.class.getDeclaredMethods())
            .map(method -> Arguments.of(method, null, "value", ChildValue.class.getName()));
    return Stream.concat(
        childValueMethods,
        Stream.of(
            Arguments.of(
                Greeter.class.getMethod("inherited", String.class),
                "you",
                "Hello you",
                ParentGreeter.class.getName()),
            Arguments.of(
                NumericGreeter.class.getMethod("hello", int.class),
                42,
                "Hello 42",
                NumericGreeter.class.getName()),
            Arguments.of(
                ParentGreeter.class.getMethod("covariant", String.class),
                "you",
                "Hello you",
                ParentGreeter.class.getName()),
            Arguments.of(ParentGreeter.class.getMethod("number", int.class), 42, 42, null),
            Arguments.of(NumericGreeter.class.getMethod("number", int.class), 42, 42, null),
            Arguments.of(
                Greeter.class.getMethod("shared", String.class), "you", "Hello you", null)));
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import rmi.app.Greeter;
import rmi.app.Server;

class RmiTest {

  @RegisterExtension
  public static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  private static int registryPort;
  private static Registry serverRegistry;
  private static Registry clientRegistry;

  @RegisterExtension final AutoCleanupExtension autoCleanup = AutoCleanupExtension.create();

  @BeforeAll
  static void setUp() throws Exception {
    registryPort = PortUtils.findOpenPort();
    serverRegistry = LocateRegistry.createRegistry(registryPort);
    clientRegistry = LocateRegistry.getRegistry("localhost", registryPort);
  }

  @AfterAll
  static void cleanUp() throws Exception {
    UnicastRemoteObject.unexportObject(serverRegistry, true);
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
    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("parent")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid()),
                        span ->
                            assertThat(span)
                                .hasName("rmi.app.Greeter/hello")
                                .hasKind(SpanKind.CLIENT)
                                .hasParentSpanId(trace.get(0).getSpanId())
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(SemanticAttributes.RPC_SYSTEM, "java_rmi"),
                                                entry(
                                                    SemanticAttributes.RPC_SERVICE,
                                                    "rmi.app.Greeter"),
                                                entry(SemanticAttributes.RPC_METHOD, "hello"))),
                        span ->
                            assertThat(span)
                                .hasName("rmi.app.Server/hello")
                                .hasKind(SpanKind.SERVER)
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(SemanticAttributes.RPC_SYSTEM, "java_rmi"),
                                                entry(
                                                    SemanticAttributes.RPC_SERVICE,
                                                    "rmi.app.Server"),
                                                entry(SemanticAttributes.RPC_METHOD, "hello")))));
  }

  @Test
  void serverBuiltinMethods() throws Exception {
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
  void serviceThrownException() throws Exception {
    Server server = new Server();
    serverRegistry.rebind(Server.RMI_ID, server);
    autoCleanup.deferCleanup(() -> serverRegistry.unbind(Server.RMI_ID));

    Throwable thrown =
        catchThrowableOfType(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      Greeter client = (Greeter) clientRegistry.lookup(Server.RMI_ID);
                      client.exceptional();
                    }),
            IllegalStateException.class);

    assertThat(testing.waitForTraces(1))
        .satisfiesExactly(
            trace ->
                assertThat(trace)
                    .satisfiesExactly(
                        span ->
                            assertThat(span)
                                .hasName("parent")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParentSpanId(SpanId.getInvalid()),
                        span ->
                            assertThat(span)
                                .hasName("rmi.app.Greeter/exceptional")
                                .hasKind(SpanKind.CLIENT)
                                .hasParentSpanId(trace.get(0).getSpanId())
                                .hasEventsSatisfying(
                                    events -> {
                                      // TODO(anuraaga): Switch to varargs events assertion
                                      // https://github.com/open-telemetry/opentelemetry-java/pull/3377/files
                                      assertThat(events).hasSize(1);
                                      assertThat(events.get(0))
                                          .hasName(SemanticAttributes.EXCEPTION_EVENT_NAME)
                                          .hasAttributesSatisfying(
                                              attrs -> {
                                                assertThat(attrs)
                                                    .hasSize(3)
                                                    .containsEntry(
                                                        SemanticAttributes.EXCEPTION_TYPE,
                                                        thrown.getClass().getCanonicalName())
                                                    .containsEntry(
                                                        SemanticAttributes.EXCEPTION_MESSAGE,
                                                        thrown.getMessage());
                                                assertThat(
                                                        attrs.get(
                                                            SemanticAttributes
                                                                .EXCEPTION_STACKTRACE))
                                                    .isInstanceOf(String.class);
                                              });
                                    })
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(SemanticAttributes.RPC_SYSTEM, "java_rmi"),
                                                entry(
                                                    SemanticAttributes.RPC_SERVICE,
                                                    "rmi.app.Greeter"),
                                                entry(
                                                    SemanticAttributes.RPC_METHOD, "exceptional"))),
                        span ->
                            assertThat(span)
                                .hasName("rmi.app.Server/exceptional")
                                .hasKind(SpanKind.SERVER)
                                .hasEventsSatisfying(
                                    events -> {
                                      // TODO(anuraaga): Switch to varargs events assertion
                                      // https://github.com/open-telemetry/opentelemetry-java/pull/3377/files
                                      assertThat(events).hasSize(1);
                                      assertThat(events.get(0))
                                          .hasName(SemanticAttributes.EXCEPTION_EVENT_NAME)
                                          .hasAttributesSatisfying(
                                              attrs -> {
                                                assertThat(attrs)
                                                    .hasSize(3)
                                                    .containsEntry(
                                                        SemanticAttributes.EXCEPTION_TYPE,
                                                        thrown.getClass().getCanonicalName());
                                                assertThat(
                                                        attrs.get(
                                                            SemanticAttributes.EXCEPTION_MESSAGE))
                                                    .isEqualTo(thrown.getMessage());
                                                assertThat(
                                                        attrs.get(
                                                            SemanticAttributes
                                                                .EXCEPTION_STACKTRACE))
                                                    .isInstanceOf(String.class);
                                              });
                                    })
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsOnly(
                                                entry(SemanticAttributes.RPC_SYSTEM, "java_rmi"),
                                                entry(
                                                    SemanticAttributes.RPC_SERVICE,
                                                    "rmi.app.Server"),
                                                entry(
                                                    SemanticAttributes.RPC_METHOD,
                                                    "exceptional")))));
  }
}

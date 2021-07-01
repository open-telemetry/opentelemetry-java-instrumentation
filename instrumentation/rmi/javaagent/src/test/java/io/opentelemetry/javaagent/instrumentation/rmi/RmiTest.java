package io.opentelemetry.javaagent.instrumentation.rmi;

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

  @BeforeAll
  static void setUp() throws Exception {
    registryPort = PortUtils.findOpenPort();
    serverRegistry = LocateRegistry.createRegistry(registryPort);
    clientRegistry = LocateRegistry.getRegistry("localhost", registryPort);
  }

  @AfterEach
  void unbind() throws Exception {
    serverRegistry.unbind("Server");
  }

  @AfterAll
  static void cleanUp() throws Exception {
    UnicastRemoteObject.unexportObject(serverRegistry, true);
  }

  @Test
  void clientCallCreatesSpans() throws Exception {
    Server server = new Server();
    serverRegistry.rebind(Server.RMI_ID, server);

    String response =
        runUnderTrace(
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
                                            .containsEntry(
                                                SemanticAttributes.RPC_SYSTEM, "java_rmi")
                                            .containsEntry(
                                                SemanticAttributes.RPC_SERVICE, "rmi.app.Greeter")
                                            .containsEntry(SemanticAttributes.RPC_METHOD, "hello")),
                        span ->
                            assertThat(span)
                                .hasName("rmi.app.Server/hello")
                                .hasKind(SpanKind.SERVER)
                                .hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes)
                                            .containsEntry(
                                                SemanticAttributes.RPC_SYSTEM, "java_rmi")
                                            .containsEntry(
                                                SemanticAttributes.RPC_SERVICE, "rmi.app.Server")
                                            .containsEntry(
                                                SemanticAttributes.RPC_METHOD, "hello"))));
  }
}

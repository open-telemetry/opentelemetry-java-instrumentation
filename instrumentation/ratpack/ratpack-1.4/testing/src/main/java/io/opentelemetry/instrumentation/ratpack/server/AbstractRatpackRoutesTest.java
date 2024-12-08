/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_QUERY;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.path.PathBinding;
import ratpack.server.RatpackServer;
import ratpack.server.RatpackServerSpec;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractRatpackRoutesTest {

  private static RatpackServer app;
  private static WebClient client;

  protected abstract InstrumentationExtension testing();

  protected abstract void configure(RatpackServerSpec serverSpec) throws Exception;

  protected abstract boolean hasHandlerSpan();

  @BeforeAll
  void setUp() throws Exception {
    app =
        RatpackServer.start(
            ratpackServerSpec -> {
              ratpackServerSpec.serverConfig(
                  serverConfigBuilder -> {
                    serverConfigBuilder.port(PortUtils.findOpenPort());
                    serverConfigBuilder.address(InetAddress.getByName("localhost"));
                  });

              Action<? super Chain> action =
                  chain ->
                      chain.all(
                          context ->
                              context.render(context.get(PathBinding.class).getDescription()));
              ratpackServerSpec.handlers(
                  chain -> {
                    chain.prefix("a", action);
                    chain.prefix("b/::\\d+", action);
                    chain.prefix("c/:val?", action);
                    chain.prefix("d/:val", action);
                    chain.prefix("e/:val?:\\d+", action);
                    chain.prefix("f/:val:\\d+", action);
                  });

              configure(ratpackServerSpec);
            });

    // Force HTTP/1 with h1c to prevent tracing of upgrade request.
    client = WebClient.of("h1c://localhost:" + app.getBindPort());
  }

  @AfterAll
  void cleanUp() throws Exception {
    app.stop();
  }

  private static Stream<Arguments> provideBindingArguments() {
    return Stream.of(
        Arguments.of("a", "a"),
        Arguments.of("b/123", "b/::\\d+"),
        Arguments.of("c", "c/:val?"),
        Arguments.of("c/123", "c/:val?"),
        Arguments.of("c/foo", "c/:val?"),
        Arguments.of("d/123", "d/:val"),
        Arguments.of("d/foo", "d/:val"),
        Arguments.of("e", "e/:val?:\\d+"),
        Arguments.of("e/123", "e/:val?:\\d+"),
        Arguments.of("e/foo", "e/:val?:\\d+"),
        Arguments.of("f/123", "f/:val:\\d+"));
  }

  @ParameterizedTest
  @MethodSource("provideBindingArguments")
  void bindingsForPath(String path, String route) {
    AggregatedHttpResponse response = client.get(path).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo(route);

    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
              assertions.add(
                  span ->
                      span.hasName("GET /" + route)
                          .hasKind(SpanKind.SERVER)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                              equalTo(SERVER_ADDRESS, "localhost"),
                              equalTo(SERVER_PORT, app.getBindPort()),
                              equalTo(CLIENT_ADDRESS, hasHandlerSpan() ? "127.0.0.1" : null),
                              equalTo(NETWORK_PEER_ADDRESS, hasHandlerSpan() ? "127.0.0.1" : null),
                              satisfies(
                                  NETWORK_PEER_PORT,
                                  port ->
                                      port.satisfiesAnyOf(
                                          val -> assertThat(val).isInstanceOf(Long.class),
                                          val -> assertThat(val).isNull())),
                              satisfies(
                                  NETWORK_PEER_PORT,
                                  val -> {
                                    if (hasHandlerSpan()) {
                                      val.isInstanceOf(Long.class);
                                    } else {
                                      val.isNull();
                                    }
                                  }),
                              equalTo(HTTP_REQUEST_METHOD, "GET"),
                              equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                              satisfies(USER_AGENT_ORIGINAL, val -> val.isInstanceOf(String.class)),
                              equalTo(URL_SCHEME, "http"),
                              equalTo(URL_PATH, "/" + path),
                              satisfies(URL_QUERY, val -> val.isNullOrEmpty()),
                              equalTo(HTTP_ROUTE, "/" + route)));
              if (hasHandlerSpan()) {
                assertions.add(
                    span ->
                        span.hasName("/" + route)
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributes(Attributes.empty()));
              }

              trace.hasSpansSatisfyingExactly(assertions);
            });
  }
}

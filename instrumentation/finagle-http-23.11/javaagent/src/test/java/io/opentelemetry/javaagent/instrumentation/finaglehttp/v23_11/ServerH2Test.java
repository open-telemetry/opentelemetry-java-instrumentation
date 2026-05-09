/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolSpecificEvent.SWITCHING_PROTOCOLS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.twitter.finagle.Http;
import com.twitter.finagle.Service;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.http2.param.PriorKnowledge;
import com.twitter.util.Await;
import com.twitter.util.Duration;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolSpecificEvent;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.EventDataAssert;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import io.opentelemetry.testing.internal.armeria.internal.shaded.guava.collect.ImmutableMap;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ServerH2Test extends AbstractServerTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Override
  protected Http.Server configureServer(Http.Server in) {
    return super.configureServer(in)
        // when enabled, supports protocol h1 & h2, the latter with upgrade
        .withHttp2()
        // todo implement http/2-specific tests
        //  the armeria configuration used at the heart of AbstractHttpServerTest isn't configurable
        //  to http/2
        .configured(PriorKnowledge.apply(true).mk());
  }

  private static void assertSwitchingProtocolsEvent(EventDataAssert eventDataAssert) {
    eventDataAssert
        .hasName(SWITCHING_PROTOCOLS.eventName())
        .hasAttributesSatisfyingExactly(
            equalTo(ProtocolSpecificEvent.SWITCHING_PROTOCOLS_FROM_KEY, "HTTP/1.1"),
            equalTo(ProtocolSpecificEvent.SWITCHING_PROTOCOLS_TO_KEY, singletonList("h2c")));
  }

  /* Bonus is that this implicitly tests both the server & client h2 upgrades. */
  @Test
  void h2ProtocolUpgrade() throws Exception {
    URI uri = URI.create("http://localhost:" + port + SUCCESS.getPath());
    Service<Request, Response> client =
        Utils.createClient(Utils.ClientType.DEFAULT)
            // must use http2 here
            .withHttp2()
            .newService(uri.getHost() + ":" + uri.getPort());
    cleanup.deferCleanup(client::close);

    Response response =
        testing.runWithSpan(
            "h2-upgrade-client",
            () ->
                Await.result(
                    client.apply(
                        Utils.buildRequest(
                            "GET",
                            uri,
                            ImmutableMap.of(
                                HttpHeaderNames.USER_AGENT.toString(),
                                TEST_USER_AGENT,
                                HttpHeaderNames.X_FORWARDED_FOR.toString(),
                                TEST_CLIENT_IP))),
                    Duration.fromSeconds(20)));

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentString()).isEqualTo(SUCCESS.getBody());

    String method = "GET";
    ServerEndpoint endpoint = SUCCESS;

    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> spanAssertions = new ArrayList<>();
          // client initiation
          spanAssertions.add(
              s -> s.hasName("h2-upgrade-client").hasNoParent().hasKind(SpanKind.INTERNAL));
          // actual client netty span (including upgrade event)
          spanAssertions.add(
              s ->
                  s.hasKind(SpanKind.CLIENT)
                      .hasName(method)
                      .hasParent(trace.getSpan(0))
                      .hasEventsSatisfyingExactly(ServerH2Test::assertSwitchingProtocolsEvent));
          // server netty span (including upgrade event)
          spanAssertions.add(
              span ->
                  assertServerSpan(span, method, endpoint, endpoint.getStatus())
                      .hasParent(trace.getSpan(1))
                      .hasEventsSatisfyingExactly(ServerH2Test::assertSwitchingProtocolsEvent));
          // server controller span
          spanAssertions.add(
              span -> {
                assertControllerSpan(span, null);
                span.hasParent(trace.getSpan(2));
              });

          trace.hasSpansSatisfyingExactly(spanAssertions);
        });
  }
}

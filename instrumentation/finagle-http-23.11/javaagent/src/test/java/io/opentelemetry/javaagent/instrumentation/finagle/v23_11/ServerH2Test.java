/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finagle.v23_11;

import static io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolSpecificEvents.SWITCHING_PROTOCOLS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.twitter.finagle.Http;
import com.twitter.finagle.ListeningServer;
import com.twitter.finagle.Service;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.http2.param.PriorKnowledge;
import com.twitter.util.Await;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.netty.v4_1.internal.ProtocolSpecificEvents;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.EventDataAssert;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import io.opentelemetry.testing.internal.armeria.internal.shaded.guava.collect.ImmutableMap;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.AssertAccess;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ServerH2Test extends AbstractServerTest {

  @RegisterExtension
  static final FinagleClientExtension extension = FinagleClientExtension.http_2();

  @Override
  protected ListeningServer setupServer() {
    return Http.server()
        // when enabled, supports protocol h1 & h2, the latter with upgrade
        .withHttp2()
        // todo implement http/2-specific tests
        //  the armeria configuration used at the heart of AbstractHttpServerTest isn't configurable
        //  to http/2
        .configured(PriorKnowledge.apply(true).mk())
        .serve(address.getHost() + ":" + port, new AbstractServerTest.TestService());
  }

  private static void assertSwitchingProtocolsEvent(EventDataAssert eventDataAssert) {
    eventDataAssert
        .hasName(SWITCHING_PROTOCOLS.eventName())
        .hasAttributes(
            Attributes.of(
                ProtocolSpecificEvents.SWITCHING_PROTOCOLS_FROM_KEY,
                "HTTP/1.1",
                ProtocolSpecificEvents.SWITCHING_PROTOCOLS_TO_KEY,
                Collections.singletonList("h2c")));
  }

  @Test
  void h2ProtocolUpgrade() throws Exception {
    URI uri = URI.create("http://localhost:" + port + SUCCESS.getPath());
    Service<Request, Response> client = extension.clientFor(uri);
    Response response =
        Await.result(
            client.apply(
                FinagleClientExtension.buildRequest(
                    "GET",
                    uri,
                    ImmutableMap.of(
                        HttpHeaderNames.USER_AGENT.toString(),
                        TEST_USER_AGENT,
                        HttpHeaderNames.X_FORWARDED_FOR.toString(),
                        TEST_CLIENT_IP))),
            com.twitter.util.Duration.fromSeconds(20));

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentString()).isEqualTo(SUCCESS.getBody());

    String method = "GET";
    ServerEndpoint endpoint = SUCCESS;

    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> spanAssertions = new ArrayList<>();
          spanAssertions.add(
              s -> s.hasEventsSatisfyingExactly(ServerH2Test::assertSwitchingProtocolsEvent));
          spanAssertions.add(
              span -> {
                assertServerSpan(span, method, endpoint, endpoint.getStatus());
                span.hasEventsSatisfyingExactly(ServerH2Test::assertSwitchingProtocolsEvent);
              });

          int parentIndex = 1;
          spanAssertions.add(
              span -> {
                assertControllerSpan(span, null);
                span.hasParent(trace.getSpan(parentIndex));
              });

          trace.hasSpansSatisfyingExactly(spanAssertions);

          List<SpanData> spanData = AssertAccess.getActual(trace);
          SpanData clientSpan = spanData.get(0);
          SpanData controllerSpan = spanData.get(2);
          // not testing client.end > server.end bc that's not always guaranteed to be true
          // depending on how resources are closed and torn down
          Assertions.assertThat(clientSpan.getEndEpochNanos())
              .isGreaterThanOrEqualTo(controllerSpan.getEndEpochNanos());
        });
  }
}

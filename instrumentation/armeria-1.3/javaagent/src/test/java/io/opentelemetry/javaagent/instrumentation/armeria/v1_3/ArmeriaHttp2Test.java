/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArmeriaHttp2Test {
  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  static ServerExtension server1 =
      new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
          sb.service("/", (ctx, req) -> HttpResponse.of("hello"));
        }
      };

  @RegisterExtension
  static ServerExtension server2 =
      new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
          sb.service("/", (ctx, req) -> createWebClient(server1).execute(req));
        }
      };

  private static WebClient createWebClient(ServerExtension server) {
    return WebClient.builder(server.httpUri()).build();
  }

  @Test
  void testHello() throws Exception {
    // verify that spans are created and context is propagated
    AggregatedHttpResponse result = createWebClient(server2).get("/").aggregate().get();
    assertThat(result.contentAscii()).isEqualTo("hello");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasNoParent(),
                span -> span.hasName("GET /").hasKind(SpanKind.SERVER).hasParent(trace.getSpan(0)),
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("GET /").hasKind(SpanKind.SERVER).hasParent(trace.getSpan(2))));
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0;

import static org.apache.pekko.http.javadsl.server.PathMatchers.integerSegment;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PekkoHttpServerJavaRouteTest extends AllDirectives {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final WebClient client = WebClient.of();

  @Test
  void testRoute() {
    ActorSystem system = ActorSystem.create("my-system");
    int port = PortUtils.findOpenPort();
    Http http = Http.get(system);

    Route route =
        concat(
            pathEndOrSingleSlash(() -> complete("root")),
            pathPrefix(
                "test",
                () ->
                    concat(
                        pathSingleSlash(() -> complete("test")),
                        path(integerSegment(), (i) -> complete("ok")))));

    CompletionStage<ServerBinding> binding = http.newServerAt("localhost", port).bind(route);
    try {
      AggregatedHttpRequest request =
          AggregatedHttpRequest.of(HttpMethod.GET, "h1c://localhost:" + port + "/test/1");
      AggregatedHttpResponse response = client.execute(request).aggregate().join();

      assertThat(response.status().code()).isEqualTo(200);
      assertThat(response.contentUtf8()).isEqualTo("ok");

      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("GET /test/*")));
    } finally {
      binding.thenCompose(ServerBinding::unbind).thenAccept(unbound -> system.terminate());
    }
  }
}

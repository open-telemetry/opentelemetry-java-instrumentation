/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Tests the case where "controller" span is created within the route handler method scope, and the
 *
 * <p>{@code Mono<ServerResponse>} from a handler is already a fully constructed response with no
 * deferred actions. For exception endpoint, the exception is thrown within route handler method
 * scope.
 */
public class ImmediateHandlerSpringWebFluxServerTest extends HandlerSpringWebFluxServerTest {
  @Override
  protected Class<?> getApplicationClass() {
    return Application.class;
  }

  @Configuration
  @EnableAutoConfiguration
  static class Application {
    @Bean
    RouterFunction<ServerResponse> router() {
      return new RouteFactory().createRoutes();
    }

    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      return new NettyReactiveWebServerFactory();
    }
  }

  static class RouteFactory extends ServerTestRouteFactory {

    @Override
    protected Mono<ServerResponse> wrapResponse(
        ServerEndpoint endpoint, Mono<ServerResponse> response, Runnable spanAction) {
      return controller(
          endpoint,
          () -> {
            spanAction.run();
            return response;
          });
    }
  }

  @Test
  void nestedPath() {
    assumeTrue(Boolean.getBoolean("testLatestDeps"));

    String method = "GET";
    AggregatedHttpRequest request = request(NESTED_PATH, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();
    assertThat(response.status().code()).isEqualTo(NESTED_PATH.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(NESTED_PATH.getBody());
    assertResponseHasCustomizedHeaders(response, NESTED_PATH, null);

    assertTheTraces(1, null, null, null, method, NESTED_PATH);
  }
}

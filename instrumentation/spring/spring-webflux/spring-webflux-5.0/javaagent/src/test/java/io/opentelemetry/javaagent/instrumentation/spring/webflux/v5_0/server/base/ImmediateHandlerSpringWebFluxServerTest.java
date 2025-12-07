/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.base;

import io.opentelemetry.instrumentation.spring.webflux.server.AbstractImmediateHandlerSpringWebFluxServerTest;

class ImmediateHandlerSpringWebFluxServerTest
    extends AbstractImmediateHandlerSpringWebFluxServerTest {
  //  @Override
  //  protected Class<?> getApplicationClass() {
  //    return Application.class;
  //  }
  //
  //  @Configuration
  //  @EnableAutoConfiguration
  //  static class Application {
  //    @Bean
  //    RouterFunction<ServerResponse> router() {
  //      return new RouteFactory().createRoutes();
  //    }
  //
  //    @Bean
  //    NettyReactiveWebServerFactory nettyFactory() {
  //      return new NettyReactiveWebServerFactory();
  //    }
  //  }
  //
  //  static class RouteFactory extends ServerTestRouteFactory {
  //
  //    @Override
  //    protected Mono<ServerResponse> wrapResponse(
  //        ServerEndpoint endpoint, Mono<ServerResponse> response, Runnable spanAction) {
  //      return controller(
  //          endpoint,
  //          () -> {
  //            spanAction.run();
  //            return response;
  //          });
  //    }
  //  }
  //
  //  @Test
  //  void nestedPath() {
  //    assumeTrue(Boolean.getBoolean("testLatestDeps"));
  //
  //    String method = "GET";
  //    AggregatedHttpRequest request = request(NESTED_PATH, method);
  //    AggregatedHttpResponse response = client.execute(request).aggregate().join();
  //    assertThat(response.status().code()).isEqualTo(NESTED_PATH.getStatus());
  //    assertThat(response.contentUtf8()).isEqualTo(NESTED_PATH.getBody());
  //    assertResponseHasCustomizedHeaders(response, NESTED_PATH, null);
  //
  //    assertTheTraces(1, null, null, null, method, NESTED_PATH);
  //  }
}

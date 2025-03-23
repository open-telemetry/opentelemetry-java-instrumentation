/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
public class RedirectComponent {
  @Bean
  public RouterFunction<ServerResponse> redirectRouterFunction() {
    return route(
        GET("/double-greet-redirect"),
        req -> ServerResponse.temporaryRedirect(URI.create("/double-greet")).build());
  }
}

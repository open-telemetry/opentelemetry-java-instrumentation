/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.webmvc.v5_0;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;

import java.io.IOException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@SpringBootApplication
public class Gateway43MvcTestApplication {
  @Bean
  public RouterFunction<ServerResponse> gatewayRouterFunction() {
    HandlerFunction<ServerResponse> echoHandler =
        request -> {
          try {
            String body = request.body(String.class);
            return ServerResponse.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
          } catch (IOException e) {
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
          }
        };

    return route("test-route-id")
        .POST("/gateway/echo", echoHandler)
        .before(uri("http://mock.response"))
        .build();
  }
}

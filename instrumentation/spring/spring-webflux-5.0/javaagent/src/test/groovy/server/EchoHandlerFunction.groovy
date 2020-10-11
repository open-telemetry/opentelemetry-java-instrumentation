/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server


import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

class EchoHandlerFunction implements HandlerFunction<ServerResponse> {

  EchoHandler echoHandler

  EchoHandlerFunction(EchoHandler echoHandler) {
    this.echoHandler = echoHandler
  }

  @Override
  Mono<ServerResponse> handle(ServerRequest request) {
    return echoHandler.echo(request)
  }
}

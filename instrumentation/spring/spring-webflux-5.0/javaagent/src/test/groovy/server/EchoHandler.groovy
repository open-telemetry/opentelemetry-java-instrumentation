/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server


import io.opentelemetry.extension.annotations.WithSpan
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Component
class EchoHandler {

  @WithSpan("echo")
  Mono<ServerResponse> echo(ServerRequest request) {
    return ServerResponse.accepted().contentType(MediaType.TEXT_PLAIN)
      .body(request.bodyToMono(String), String)
  }
}

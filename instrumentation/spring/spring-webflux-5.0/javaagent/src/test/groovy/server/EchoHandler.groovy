/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Component
class EchoHandler {

  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test")

  Mono<ServerResponse> echo(ServerRequest request) {
    tracer.spanBuilder("echo").startSpan().end()
    return ServerResponse.accepted().contentType(MediaType.TEXT_PLAIN)
      .body(request.bodyToMono(String), String)
  }
}

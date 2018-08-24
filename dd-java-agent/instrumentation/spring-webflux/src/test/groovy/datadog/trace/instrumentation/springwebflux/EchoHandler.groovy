package datadog.trace.instrumentation.springwebflux

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Component
class EchoHandler {
  Mono<ServerResponse> echo(ServerRequest request) {
    return ServerResponse.accepted().contentType(MediaType.TEXT_PLAIN)
      .body(request.bodyToMono(String), String)
  }
}

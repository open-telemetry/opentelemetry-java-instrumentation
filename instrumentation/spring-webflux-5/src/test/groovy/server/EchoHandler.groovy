package server

import io.opentelemetry.OpenTelemetry
import io.opentelemetry.trace.Tracer
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

/**
 * Note: this class has to stay outside of 'io.opentelemetry.auto.*' package because we need
 * it transformed by {@code @Trace} annotation.
 */
@Component
class EchoHandler {

  private static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto")

  Mono<ServerResponse> echo(ServerRequest request) {
    TRACER.spanBuilder("echo").startSpan().end()
    return ServerResponse.accepted().contentType(MediaType.TEXT_PLAIN)
      .body(request.bodyToMono(String), String)
  }
}

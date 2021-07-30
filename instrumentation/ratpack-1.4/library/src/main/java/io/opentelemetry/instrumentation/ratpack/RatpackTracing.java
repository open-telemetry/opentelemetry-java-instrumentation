package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import ratpack.handling.HandlerDecorator;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.registry.RegistrySpec;

public final class RatpackTracing {

  public static RatpackTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry).build();
  }

  public static RatpackTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new RatpackTracingBuilder(openTelemetry);
  }

  private final OpenTelemetryHandler serverHandler;

  RatpackTracing(Instrumenter<Request, Response> serverInstrumenter) {
    serverHandler = new OpenTelemetryHandler(serverInstrumenter);
  }

  public void configureServerRegistry(RegistrySpec registry) {
    registry.add(HandlerDecorator.prepend(serverHandler));
  }
}

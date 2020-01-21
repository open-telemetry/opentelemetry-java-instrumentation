package io.opentelemetry.auto.instrumentation.grpc.server;

import io.grpc.Metadata;
import io.opentelemetry.context.propagation.HttpTextFormat;

public final class GrpcExtractAdapter implements HttpTextFormat.Getter<Metadata> {

  public static final GrpcExtractAdapter GETTER = new GrpcExtractAdapter();

  @Override
  public String get(final Metadata carrier, final String key) {
    return carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
  }
}

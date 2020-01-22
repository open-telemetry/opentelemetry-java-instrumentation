package io.opentelemetry.auto.instrumentation.grpc.client;

import io.grpc.Metadata;
import io.opentelemetry.context.propagation.HttpTextFormat;

public final class GrpcInjectAdapter implements HttpTextFormat.Setter<Metadata> {

  public static final GrpcInjectAdapter SETTER = new GrpcInjectAdapter();

  @Override
  public void put(final Metadata carrier, final String key, final String value) {
    carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
  }
}

package datadog.trace.instrumentation.grpc.server;

import datadog.trace.instrumentation.api.AgentPropagation;
import io.grpc.Metadata;

public final class GrpcExtractAdapter implements AgentPropagation.Getter<Metadata> {

  public static final GrpcExtractAdapter GETTER = new GrpcExtractAdapter();

  @Override
  public Iterable<String> keys(final Metadata carrier) {
    return carrier.keys();
  }

  @Override
  public String get(final Metadata carrier, final String key) {
    return carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
  }
}

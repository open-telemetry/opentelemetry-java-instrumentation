package io.opentelemetry.auto.instrumentation.grpc.server;

import io.grpc.Metadata;
import io.opentelemetry.auto.instrumentation.api.AgentPropagation;
import java.util.ArrayList;
import java.util.List;

public final class GrpcExtractAdapter implements AgentPropagation.Getter<Metadata> {

  public static final GrpcExtractAdapter GETTER = new GrpcExtractAdapter();

  @Override
  public Iterable<String> keys(final Metadata carrier) {
    final List<String> keys = new ArrayList<>();

    for (final String key : carrier.keys()) {
      if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
        keys.add(key);
      }
    }

    return keys;
  }

  @Override
  public String get(final Metadata carrier, final String key) {
    return carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
  }
}

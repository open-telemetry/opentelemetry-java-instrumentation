package datadog.trace.instrumentation.grpc.client;

import io.grpc.Metadata;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;

public final class GrpcInjectAdapter implements TextMap {
  private final Metadata metadata;

  public GrpcInjectAdapter(final Metadata metadata) {
    this.metadata = metadata;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException(
        "GrpcInjectAdapter should only be used with Tracer.inject()");
  }

  @Override
  public void put(final String key, final String value) {
    this.metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
  }
}

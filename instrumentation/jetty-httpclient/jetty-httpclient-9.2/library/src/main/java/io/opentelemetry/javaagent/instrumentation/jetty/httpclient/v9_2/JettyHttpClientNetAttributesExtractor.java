package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

public class JettyHttpClientNetAttributesExtractor
    extends NetAttributesExtractor<Request, Response> {

  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(Request request, @Nullable Response response) {
    return request != null ? request.getHost() : null;
  }

  @Override
  @Nullable
  public Integer peerPort(Request request, @Nullable Response response) {
    return request != null ? request.getPort() : null;
  }

  @Override
  @Nullable
  public String peerIp(Request request, @Nullable Response response) {
    // Return null unless the library supports resolution to something similar to SocketAddress
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/3012/files#r633188645
    return null;
  }
}

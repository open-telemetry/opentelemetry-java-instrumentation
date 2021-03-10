package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.instrumentation.api.instrumenter.NetExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ArmeriaNetExtractor extends NetExtractor<RequestContext, RequestLog> {

  static final ArmeriaNetExtractor INSTANCE = new ArmeriaNetExtractor();

  @Override
  protected String transport(
      RequestContext requestContext) {
    return SemanticAttributes.NetTransportValues.IP_TCP.getValue();
  }

  @Override
  protected @Nullable String peerName(RequestContext requestContext) {
    return null;
  }

  @Override
  protected Long peerPort(RequestContext requestContext) {
    int port = request(requestContext).uri().getPort();
    if (port != -1) {
      return (long) port;
    }
    return null;
  }

  @Override
  protected String peerIp(RequestContext requestContext,
      RequestLog requestLog) {
    SocketAddress address = requestContext.remoteAddress();
    if (address instanceof InetSocketAddress) {
      return ((InetSocketAddress) address).getAddress().getHostAddress();
    }
    return null;
  }

  private HttpRequest request(RequestContext ctx) {
    HttpRequest request = ctx.request();
    if (request == null) {
      throw new IllegalStateException("Context always has a request in decorators, this exception indicates a programming bug.");
    }
    return request;
  }
}

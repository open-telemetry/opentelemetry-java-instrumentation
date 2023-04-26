package io.opentelemetry.javaagent.instrumentation.thrift.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.thrift.ThriftRequest;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class ThriftNetServerAttributesGetter extends InetSocketAddressNetServerAttributesGetter<ThriftRequest>{
  @Override
  public String getTransport(ThriftRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getHostName(ThriftRequest thriftRequest) {
    return thriftRequest.host;
  }

  @Override
  public Integer getHostPort(ThriftRequest thriftRequest) {
    return thriftRequest.port;
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(ThriftRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected InetSocketAddress getHostSocketAddress(ThriftRequest thriftRequest) {
    return null;
  }
}

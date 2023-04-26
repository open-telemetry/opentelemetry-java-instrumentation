package io.opentelemetry.javaagent.instrumentation.thrift.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.thrift.ThriftRequest;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class ThriftNetClientAttributesGetter extends InetSocketAddressNetClientAttributesGetter<ThriftRequest,Integer> {
  @Override
  public String getTransport(ThriftRequest request, @Nullable Integer response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getPeerName(ThriftRequest thriftRequest) {
    return null;
  }

  @Override
  public Integer getPeerPort(ThriftRequest thriftRequest) {
    return 10;
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(ThriftRequest request, @Nullable Integer response) {

    return null;
  }
}



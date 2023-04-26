/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.thrift.ThriftRequest;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ThriftNetServerAttributesGetter
    extends InetSocketAddressNetServerAttributesGetter<ThriftRequest> {
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

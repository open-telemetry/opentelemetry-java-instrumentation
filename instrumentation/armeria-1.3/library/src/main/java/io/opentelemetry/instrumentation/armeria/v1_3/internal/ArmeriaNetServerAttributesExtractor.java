/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public final class ArmeriaNetServerAttributesExtractor
    extends InetSocketAddressNetServerAttributesExtractor<RequestContext, RequestLog> {

  @Override
  public String transport(RequestContext ctx) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public InetSocketAddress getAddress(RequestContext ctx) {
    SocketAddress address = ctx.remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}

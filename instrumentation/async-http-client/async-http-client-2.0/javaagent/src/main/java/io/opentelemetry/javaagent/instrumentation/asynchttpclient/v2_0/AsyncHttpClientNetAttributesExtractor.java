/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetResponseAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.asynchttpclient.Response;
import org.checkerframework.checker.nullness.qual.Nullable;

final class AsyncHttpClientNetAttributesExtractor
    extends InetSocketAddressNetResponseAttributesExtractor<RequestContext, Response> {

  @Override
  public String transport(@Nullable Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable InetSocketAddress getAddress(@Nullable Response response) {
    if (response != null && response.getRemoteAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) response.getRemoteAddress();
    }
    return null;
  }
}

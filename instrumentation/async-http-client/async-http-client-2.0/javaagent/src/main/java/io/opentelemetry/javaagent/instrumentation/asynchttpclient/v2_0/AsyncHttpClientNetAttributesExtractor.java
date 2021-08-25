/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import org.checkerframework.checker.nullness.qual.Nullable;

final class AsyncHttpClientNetAttributesExtractor
    extends InetSocketAddressNetAttributesExtractor<Request, Response> {

  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable InetSocketAddress getAddress(Request request, @Nullable Response response) {
    if (response != null && response.getRemoteAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) response.getRemoteAddress();
    }
    return null;
  }
}

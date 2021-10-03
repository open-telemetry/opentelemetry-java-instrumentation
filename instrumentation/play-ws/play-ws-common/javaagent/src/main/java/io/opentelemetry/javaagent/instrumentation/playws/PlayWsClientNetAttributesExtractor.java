/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetResponseAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

final class PlayWsClientNetAttributesExtractor
    extends InetSocketAddressNetResponseAttributesExtractor<Request, Response> {

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

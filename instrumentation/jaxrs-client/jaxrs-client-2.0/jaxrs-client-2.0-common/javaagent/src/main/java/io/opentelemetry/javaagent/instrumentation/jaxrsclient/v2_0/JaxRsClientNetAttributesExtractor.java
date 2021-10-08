/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JaxRsClientNetAttributesExtractor
    extends NetClientAttributesExtractor<ClientRequestContext, ClientResponseContext> {

  @Override
  public String transport(ClientRequestContext request, @Nullable ClientResponseContext response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(
      ClientRequestContext request, @Nullable ClientResponseContext response) {
    return request.getUri().getHost();
  }

  @Override
  public Integer peerPort(ClientRequestContext request, @Nullable ClientResponseContext response) {
    int port = request.getUri().getPort();
    if (port != -1) {
      return port;
    }
    return null;
  }

  @Override
  public @Nullable String peerIp(
      ClientRequestContext request, @Nullable ClientResponseContext response) {
    return null;
  }
}

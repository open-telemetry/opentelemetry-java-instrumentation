/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;

final class JaxRsClientNetAttributesGetter
    implements NetClientAttributesGetter<ClientRequestContext, ClientResponseContext> {

  @Override
  public String transport(ClientRequestContext request, @Nullable ClientResponseContext response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(ClientRequestContext request, @Nullable ClientResponseContext response) {
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
  @Nullable
  public String peerIp(ClientRequestContext request, @Nullable ClientResponseContext response) {
    return null;
  }
}

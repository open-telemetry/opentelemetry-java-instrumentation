/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesAdapter;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

final class ResteasyClientNetAttributesAdapter
    implements NetAttributesAdapter<ClientInvocation, Response> {

  @Override
  public String transport(ClientInvocation request, @Nullable Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(ClientInvocation request, @Nullable Response response) {
    return request.getUri().getHost();
  }

  @Override
  public Integer peerPort(ClientInvocation request, @Nullable Response response) {
    int port = request.getUri().getPort();
    if (port != -1) {
      return port;
    }
    return null;
  }

  @Override
  @Nullable
  public String peerIp(ClientInvocation request, @Nullable Response response) {
    return null;
  }
}

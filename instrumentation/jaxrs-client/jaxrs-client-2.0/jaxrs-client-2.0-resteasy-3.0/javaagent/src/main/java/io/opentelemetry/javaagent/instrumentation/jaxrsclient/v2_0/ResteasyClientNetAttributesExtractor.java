/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.ws.rs.core.Response;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

final class ResteasyClientNetAttributesExtractor
    extends NetClientAttributesExtractor<ClientInvocation, Response> {

  @Override
  public String transport(ClientInvocation request, @Nullable Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(ClientInvocation request, @Nullable Response response) {
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
  public @Nullable String peerIp(ClientInvocation request, @Nullable Response response) {
    return null;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v1_1;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JaxRsClientNetAttributesExtractor
    extends NetAttributesExtractor<ClientRequest, ClientResponse> {

  @Override
  public String transport(ClientRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(ClientRequest request) {
    return request.getURI().getHost();
  }

  @Override
  public Integer peerPort(ClientRequest request) {
    int port = request.getURI().getPort();
    if (port != -1) {
      return port;
    }
    return null;
  }

  @Override
  public @Nullable String peerIp(ClientRequest request) {
    return null;
  }
}

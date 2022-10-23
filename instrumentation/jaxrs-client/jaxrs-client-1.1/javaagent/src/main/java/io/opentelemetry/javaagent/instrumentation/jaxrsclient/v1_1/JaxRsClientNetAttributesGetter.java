/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v1_1;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class JaxRsClientNetAttributesGetter
    implements NetClientAttributesGetter<ClientRequest, ClientResponse> {

  @Override
  public String transport(ClientRequest request, @Nullable ClientResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(ClientRequest request) {
    return request.getURI().getHost();
  }

  @Override
  public Integer peerPort(ClientRequest request) {
    return request.getURI().getPort();
  }
}

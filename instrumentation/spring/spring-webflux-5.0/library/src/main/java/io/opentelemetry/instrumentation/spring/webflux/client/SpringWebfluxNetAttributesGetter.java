/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

final class SpringWebfluxNetAttributesGetter
    implements NetClientAttributesGetter<ClientRequest, ClientResponse> {

  @Override
  public String transport(ClientRequest request, @Nullable ClientResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String peerName(ClientRequest request, @Nullable ClientResponse response) {
    return request.url().getHost();
  }

  @Override
  public Integer peerPort(ClientRequest request, @Nullable ClientResponse response) {
    return request.url().getPort();
  }

  @Nullable
  @Override
  public String peerIp(ClientRequest request, @Nullable ClientResponse response) {
    return null;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SpringWebfluxNetAttributesGetter
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
  public String sockFamily(ClientRequest clientRequest, @Nullable ClientResponse clientResponse) {
    return null;
  }

  @Nullable
  @Override
  public String sockPeerAddr(ClientRequest clientRequest, @Nullable ClientResponse clientResponse) {
    return null;
  }

  @Nullable
  @Override
  public String sockPeerName(ClientRequest clientRequest, @Nullable ClientResponse clientResponse) {
    return null;
  }

  @Nullable
  @Override
  public Integer sockPeerPort(
      ClientRequest clientRequest, @Nullable ClientResponse clientResponse) {
    return null;
  }
}

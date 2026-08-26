/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

class WebClientHttpAttributesGetter
    extends io.opentelemetry.instrumentation.spring.webflux.v5_3.internal
        .WebClientHttpAttributesGetter {

  private static final VirtualField<ClientResponse, String> PROTOCOL_VERSION =
      VirtualField.find(ClientResponse.class, String.class);

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      ClientRequest request, @Nullable ClientResponse response) {
    return response == null ? null : PROTOCOL_VERSION.get(response);
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import static io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client.HttpProtocolVersion.CLIENT_RESPONSE_PROTOCOL_VERSION;

import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

class WebClientHttpAttributesGetter
    extends io.opentelemetry.instrumentation.spring.webflux.v5_3.internal
        .WebClientHttpAttributesGetter {

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      ClientRequest request, @Nullable ClientResponse response) {
    return response == null ? null : CLIENT_RESPONSE_PROTOCOL_VERSION.get(response);
  }
}

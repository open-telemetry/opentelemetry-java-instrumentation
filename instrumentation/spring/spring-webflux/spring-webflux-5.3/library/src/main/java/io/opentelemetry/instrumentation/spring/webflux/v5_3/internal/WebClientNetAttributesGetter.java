/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class WebClientNetAttributesGetter
    implements NetClientAttributesGetter<ClientRequest, ClientResponse> {

  @Nullable
  @Override
  public String getPeerName(ClientRequest request) {
    return request.url().getHost();
  }

  @Override
  public Integer getPeerPort(ClientRequest request) {
    return request.url().getPort();
  }
}

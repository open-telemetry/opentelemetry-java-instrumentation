/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

final class SpringWebNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, ClientHttpResponse> {

  @Override
  @Nullable
  public String getPeerName(HttpRequest httpRequest) {
    return httpRequest.getURI().getHost();
  }

  @Override
  public Integer getPeerPort(HttpRequest httpRequest) {
    return httpRequest.getURI().getPort();
  }
}

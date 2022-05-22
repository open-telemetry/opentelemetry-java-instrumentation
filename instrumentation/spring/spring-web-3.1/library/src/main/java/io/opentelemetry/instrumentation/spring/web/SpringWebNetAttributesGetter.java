/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

final class SpringWebNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, ClientHttpResponse> {
  @Override
  public String transport(HttpRequest httpRequest, @Nullable ClientHttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(HttpRequest httpRequest, @Nullable ClientHttpResponse response) {
    return httpRequest.getURI().getHost();
  }

  @Override
  public Integer peerPort(HttpRequest httpRequest, @Nullable ClientHttpResponse response) {
    return httpRequest.getURI().getPort();
  }

  @Override
  @Nullable
  public String peerIp(HttpRequest httpRequest, @Nullable ClientHttpResponse response) {
    return null;
  }
}

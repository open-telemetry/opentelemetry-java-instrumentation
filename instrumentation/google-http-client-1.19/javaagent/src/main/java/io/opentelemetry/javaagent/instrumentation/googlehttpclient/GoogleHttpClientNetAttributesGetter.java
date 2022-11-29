/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class GoogleHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String transport(HttpRequest request, @Nullable HttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(HttpRequest request) {
    return request.getUrl().getHost();
  }

  @Override
  public Integer peerPort(HttpRequest request) {
    return request.getUrl().getPort();
  }
}

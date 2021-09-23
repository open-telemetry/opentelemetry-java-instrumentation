/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class GoogleHttpClientNetAttributesExtractor
    extends NetAttributesExtractor<HttpRequest, HttpResponse> {

  @Override
  public String transport(HttpRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(HttpRequest request, @Nullable HttpResponse response) {
    return request.getUrl().getHost();
  }

  @Override
  public Integer peerPort(HttpRequest request, @Nullable HttpResponse response) {
    int port = request.getUrl().getPort();
    if (port != -1) {
      return port;
    }
    return null;
  }

  @Override
  public @Nullable String peerIp(HttpRequest request, @Nullable HttpResponse response) {
    return null;
  }
}

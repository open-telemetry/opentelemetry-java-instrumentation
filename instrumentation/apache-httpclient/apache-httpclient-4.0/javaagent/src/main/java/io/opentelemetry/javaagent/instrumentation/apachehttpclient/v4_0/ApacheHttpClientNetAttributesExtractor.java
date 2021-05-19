/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ApacheHttpClientNetAttributesExtractor
    extends NetAttributesExtractor<HttpUriRequest, HttpResponse> {

  @Override
  protected String transport(HttpUriRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  protected @Nullable String peerName(HttpUriRequest request, @Nullable HttpResponse response) {
    return request.getURI().getHost();
  }

  @Override
  protected Long peerPort(HttpUriRequest request, @Nullable HttpResponse response) {
    URI uri = request.getURI();
    int port = uri.getPort();
    if (port != -1) {
      return (long) port;
    }
    switch (uri.getScheme()) {
      case "http":
        return 80L;
      case "https":
        return 443L;
      default:
        return null;
    }
  }

  @Override
  protected @Nullable String peerIp(HttpUriRequest request, @Nullable HttpResponse response) {
    return null;
  }
}

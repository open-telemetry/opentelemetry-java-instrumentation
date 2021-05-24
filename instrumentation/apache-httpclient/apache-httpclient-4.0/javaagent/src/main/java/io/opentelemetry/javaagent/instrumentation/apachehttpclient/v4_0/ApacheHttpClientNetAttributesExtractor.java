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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApacheHttpClientNetAttributesExtractor
    extends NetAttributesExtractor<HttpUriRequest, HttpResponse> {

  private static final Logger logger =
      LoggerFactory.getLogger(ApacheHttpClientNetAttributesExtractor.class);

  @Override
  public String transport(HttpUriRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(HttpUriRequest request, @Nullable HttpResponse response) {
    return request.getURI().getHost();
  }

  @Override
  public Integer peerPort(HttpUriRequest request, @Nullable HttpResponse response) {
    URI uri = request.getURI();
    int port = uri.getPort();
    if (port != -1) {
      return port;
    }
    switch (uri.getScheme()) {
      case "http":
        return 80;
      case "https":
        return 443;
      default:
        logger.debug("no default port mapping for scheme: {}", uri.getScheme());
        return null;
    }
  }

  @Override
  public @Nullable String peerIp(HttpUriRequest request, @Nullable HttpResponse response) {
    return null;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesClientExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApacheHttpClientNetAttributesExtractor
    extends NetAttributesClientExtractor<ClassicHttpRequest, HttpResponse> {

  private static final Logger logger =
      LoggerFactory.getLogger(ApacheHttpClientNetAttributesExtractor.class);

  @Override
  public String transport(ClassicHttpRequest request, @Nullable HttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(ClassicHttpRequest request, @Nullable HttpResponse response) {
    return request.getAuthority().getHostName();
  }

  @Override
  public Integer peerPort(ClassicHttpRequest request, @Nullable HttpResponse response) {
    int port = request.getAuthority().getPort();
    if (port != -1) {
      return port;
    }
    String scheme = request.getScheme();
    if (scheme == null) {
      return 80;
    }
    switch (scheme) {
      case "http":
        return 80;
      case "https":
        return 443;
      default:
        logger.debug("no default port mapping for scheme: {}", scheme);
        return null;
    }
  }

  @Override
  public @Nullable String peerIp(ClassicHttpRequest request, @Nullable HttpResponse response) {
    return null;
  }
}

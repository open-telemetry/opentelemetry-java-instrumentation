/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesOnStartExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdkHttpNetAttributesExtractor
    extends NetAttributesOnStartExtractor<HttpRequest, HttpResponse<?>> {

  private static final Logger logger = LoggerFactory.getLogger(JdkHttpNetAttributesExtractor.class);

  @Override
  public String transport(HttpRequest httpRequest) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(HttpRequest httpRequest) {
    return httpRequest.uri().getHost();
  }

  @Override
  public @Nullable Integer peerPort(HttpRequest httpRequest) {
    int port = httpRequest.uri().getPort();
    if (port != -1) {
      return port;
    }
    String scheme = httpRequest.uri().getScheme();
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
  public @Nullable String peerIp(HttpRequest httpRequest) {
    return null;
  }
}

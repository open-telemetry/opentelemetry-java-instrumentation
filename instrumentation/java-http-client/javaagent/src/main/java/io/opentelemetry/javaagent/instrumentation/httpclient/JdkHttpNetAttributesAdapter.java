/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesAdapter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdkHttpNetAttributesAdapter
    implements NetClientAttributesAdapter<HttpRequest, HttpResponse<?>> {

  private static final Logger logger = LoggerFactory.getLogger(JdkHttpNetAttributesAdapter.class);

  @Override
  public String transport(HttpRequest httpRequest, @Nullable HttpResponse<?> response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(HttpRequest httpRequest, @Nullable HttpResponse<?> response) {
    return httpRequest.uri().getHost();
  }

  @Override
  @Nullable
  public Integer peerPort(HttpRequest httpRequest, @Nullable HttpResponse<?> response) {
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
  @Nullable
  public String peerIp(HttpRequest httpRequest, @Nullable HttpResponse<?> response) {
    return null;
  }
}

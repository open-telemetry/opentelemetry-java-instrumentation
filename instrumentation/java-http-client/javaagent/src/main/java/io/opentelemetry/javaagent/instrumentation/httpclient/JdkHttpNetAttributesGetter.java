/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class JdkHttpNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, HttpResponse<?>> {

  private static final Logger logger = Logger.getLogger(JdkHttpNetAttributesGetter.class.getName());

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
        if (logger.isLoggable(Level.FINE)) {
          logger.fine("no default port mapping for scheme: " + scheme);
        }
        return null;
    }
  }

  @Override
  @Nullable
  public String peerIp(HttpRequest httpRequest, @Nullable HttpResponse<?> response) {
    return null;
  }
}

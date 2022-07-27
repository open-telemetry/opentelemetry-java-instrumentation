/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.HttpURLConnection;
import org.checkerframework.checker.nullness.qual.Nullable;

class HttpUrlNetAttributesExtractor
    extends NetClientAttributesExtractor<HttpURLConnection, Integer> {
  @Override
  public @Nullable String transport(HttpURLConnection connection, @Nullable Integer status) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public String peerName(HttpURLConnection connection, @Nullable Integer status) {
    return connection.getURL().getHost();
  }

  @Override
  public Integer peerPort(HttpURLConnection connection, @Nullable Integer status) {
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
  public @Nullable String peerIp(HttpURLConnection connection, @Nullable Integer status) {
    return null;
  }
}

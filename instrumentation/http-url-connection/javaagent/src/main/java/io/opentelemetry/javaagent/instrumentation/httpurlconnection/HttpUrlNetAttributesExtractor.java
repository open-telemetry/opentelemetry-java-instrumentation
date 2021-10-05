/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesClientExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.HttpURLConnection;
import org.checkerframework.checker.nullness.qual.Nullable;

class HttpUrlNetAttributesExtractor
    extends NetAttributesClientExtractor<HttpURLConnection, Integer> {
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
    return connection.getURL().getPort();
  }

  @Override
  public @Nullable String peerIp(HttpURLConnection connection, @Nullable Integer status) {
    return null;
  }
}

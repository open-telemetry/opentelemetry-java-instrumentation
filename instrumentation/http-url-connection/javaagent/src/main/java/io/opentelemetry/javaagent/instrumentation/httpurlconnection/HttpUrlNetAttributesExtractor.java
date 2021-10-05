/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesServerExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.HttpURLConnection;
import org.checkerframework.checker.nullness.qual.Nullable;

class HttpUrlNetAttributesExtractor
    extends NetAttributesServerExtractor<HttpURLConnection, Integer> {
  @Override
  public @Nullable String transport(HttpURLConnection connection) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public String peerName(HttpURLConnection connection) {
    return connection.getURL().getHost();
  }

  @Override
  public Integer peerPort(HttpURLConnection connection) {
    return connection.getURL().getPort();
  }

  @Override
  public @Nullable String peerIp(HttpURLConnection connection) {
    return null;
  }
}

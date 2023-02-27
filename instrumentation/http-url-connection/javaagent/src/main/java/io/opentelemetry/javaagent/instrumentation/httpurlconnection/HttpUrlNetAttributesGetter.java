/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.HttpURLConnection;
import javax.annotation.Nullable;

class HttpUrlNetAttributesGetter implements NetClientAttributesGetter<HttpURLConnection, Integer> {

  @Override
  @Nullable
  public String getTransport(HttpURLConnection connection, @Nullable Integer status) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public String getPeerName(HttpURLConnection connection) {
    return connection.getURL().getHost();
  }

  @Override
  public Integer getPeerPort(HttpURLConnection connection) {
    return connection.getURL().getPort();
  }
}

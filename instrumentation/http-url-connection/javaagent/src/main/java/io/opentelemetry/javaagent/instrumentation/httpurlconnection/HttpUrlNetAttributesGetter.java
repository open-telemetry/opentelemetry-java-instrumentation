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
  public String transport(HttpURLConnection connection, @Nullable Integer status) {
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

  @Nullable
  @Override
  public String sockFamily(HttpURLConnection connection, @Nullable Integer status) {
    return null;
  }

  @Nullable
  @Override
  public String sockPeerAddr(HttpURLConnection connection, @Nullable Integer status) {
    return null;
  }

  @Nullable
  @Override
  public String sockPeerName(HttpURLConnection connection, @Nullable Integer status) {
    return null;
  }

  @Nullable
  @Override
  public Integer sockPeerPort(HttpURLConnection connection, @Nullable Integer status) {
    return null;
  }
}

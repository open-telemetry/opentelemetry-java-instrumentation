/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.HttpURLConnection;
import javax.annotation.Nullable;

class HttpUrlNetAttributesGetter implements NetClientAttributesGetter<HttpURLConnection, Integer> {

  @Nullable
  @Override
  public String getNetworkProtocolName(HttpURLConnection connection, @Nullable Integer integer) {
    // HttpURLConnection hardcodes the protocol name&version
    return "http";
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(HttpURLConnection connection, @Nullable Integer integer) {
    // HttpURLConnection hardcodes the protocol name&version
    return "1.1";
  }

  @Override
  public String getServerAddress(HttpURLConnection connection) {
    return connection.getURL().getHost();
  }

  @Override
  public Integer getServerPort(HttpURLConnection connection) {
    return connection.getURL().getPort();
  }
}

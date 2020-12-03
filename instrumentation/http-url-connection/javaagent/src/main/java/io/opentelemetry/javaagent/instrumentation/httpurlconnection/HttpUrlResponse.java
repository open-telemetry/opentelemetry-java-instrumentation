/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import java.net.HttpURLConnection;

public class HttpUrlResponse {
  private final HttpURLConnection connection;
  private final int resolvedResponseCode;

  public HttpUrlResponse(HttpURLConnection connection, int resolvedResponseCode) {
    this.connection = connection;
    this.resolvedResponseCode = resolvedResponseCode;
  }

  int status() {
    return resolvedResponseCode;
  }

  String header(String name) {
    return connection.getHeaderField(name);
  }
}

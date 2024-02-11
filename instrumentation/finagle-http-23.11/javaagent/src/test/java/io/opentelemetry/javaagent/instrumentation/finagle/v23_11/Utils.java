/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finagle.v23_11;

import com.twitter.finagle.http.Method;
import com.twitter.finagle.http.Request;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

final class Utils {

  private Utils() {}

  static int safePort(URI uri) {
    int port = uri.getPort();
    if (port == -1) {
      port = uri.getScheme().equals("https") ? 443 : 80;
    }
    return port;
  }

  static Request buildRequest(String method, URI uri, Map<String, String> headers) {
    Request request =
        Request.apply(
            Method.apply(method.toUpperCase(Locale.ENGLISH)),
            uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getRawQuery()));
    request.host(uri.getHost() + ":" + safePort(uri));
    headers.forEach((key, value) -> request.headerMap().put(key, value));
    return request;
  }
}

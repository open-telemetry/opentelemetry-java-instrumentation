/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7.client;

import io.netty.channel.ConnectTimeoutException;
import java.net.URI;
import org.junit.jupiter.api.condition.OS;
import ratpack.http.client.HttpClientReadTimeoutException;

public final class RatpackTestUtils {

  private RatpackTestUtils() {}

  public static Throwable ratpackClientSpanErrorMapper(URI uri, Throwable exception) {
    if (uri.toString().equals("https://192.0.2.1/")
        || (OS.WINDOWS.isCurrentOs() && uri.toString().equals("http://localhost:61/"))) {
      return new ConnectTimeoutException("Connect timeout (PT2S) connecting to " + uri);
    } else if (uri.getPath().equals("/read-timeout")) {
      return new HttpClientReadTimeoutException(
          "Read timeout (PT2S) waiting on HTTP server at " + uri);
    }
    return exception;
  }
}

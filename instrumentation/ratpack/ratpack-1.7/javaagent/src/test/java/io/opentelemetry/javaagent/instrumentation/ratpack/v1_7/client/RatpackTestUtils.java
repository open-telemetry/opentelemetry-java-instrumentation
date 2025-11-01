/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7.client;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import org.junit.jupiter.api.condition.OS;
import ratpack.http.client.HttpClientReadTimeoutException;

public final class RatpackTestUtils {

  private RatpackTestUtils() {}

  public static Throwable ratpackClientSpanErrorMapper(URI uri, Throwable exception) {
    if (uri.getPath().equals("/read-timeout")) {
      return new HttpClientReadTimeoutException(
          "Read timeout (PT2S) waiting on HTTP server at " + uri);
    }
    if (isNonRoutableAddress(uri)) {
      if (exception instanceof ClosedChannelException) {
        return OS.WINDOWS.isCurrentOs() ? exception : new PrematureChannelClosureException();
      }
      return new ConnectTimeoutException("Connect timeout (PT2S) connecting to " + uri);
    }
    if (OS.WINDOWS.isCurrentOs() && isUnopenedPort(uri)) {
      return exception;
    }
    return exception;
  }

  public static String ratpackExpectedClientSpanNameMapper(URI uri, String method) {
    if (isUnopenedPort(uri)) {
      if (OS.WINDOWS.isCurrentOs()) {
        return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
      }
      return "CONNECT";
    }
    if (isNonRoutableAddress(uri)) {
      if (OS.WINDOWS.isCurrentOs()) {
        return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
      }
      return "CONNECT";
    }
    return HttpClientTestOptions.DEFAULT_EXPECTED_CLIENT_SPAN_NAME_MAPPER.apply(uri, method);
  }

  private static boolean isNonRoutableAddress(URI uri) {
    return "192.0.2.1".equals(uri.getHost());
  }

  private static boolean isUnopenedPort(URI uri) {
    return "localhost".equals(uri.getHost()) && uri.getPort() == PortUtils.UNUSABLE_PORT;
  }
}

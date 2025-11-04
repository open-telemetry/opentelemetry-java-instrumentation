/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.condition.OS;
import reactor.netty.http.client.HttpClient;

class ReactorNettyHttpClientTest extends AbstractReactorNettyHttpClientTest {

  @Override
  HttpClient createHttpClient(boolean readTimeout) {
    return HttpClient.create()
        .tcpConfiguration(
            tcpClient -> {
              if (readTimeout) {
                tcpClient =
                    tcpClient.doOnConnected(
                        connection ->
                            connection.addHandlerLast(
                                new ReadTimeoutHandler(
                                    READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)));
              }
              return tcpClient.option(
                  ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECTION_TIMEOUT.toMillis());
            });
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);

    boolean isWindows = OS.WINDOWS.isCurrentOs();

    // Disable remote connection tests on Windows due to reactor-netty creating extra spans
    if (isWindows) {
      optionsBuilder.setTestRemoteConnection(false);
    }

    // Only run single connection tests on Linux due to networking stack differences
    if (!isWindows) {
      optionsBuilder.setSingleConnectionFactory(ReactorNettyHttpClientTest::createSingleConnection);
    }
  }

  private static SingleConnection createSingleConnection(String host, int port) {
    String url;
    try {
      url = new URL("http", host, port, "").toString();
    } catch (MalformedURLException e) {
      throw new AssertionError("Could not construct URL", e);
    }

    HttpClient httpClient = HttpClient.newConnection().baseUrl(url);

    return (path, headers) ->
        httpClient
            .headers(h -> headers.forEach(h::add))
            .get()
            .uri(path)
            .responseSingle(
                (resp, content) -> {
                  // Make sure to consume content since that's when we close the span.
                  return content.map(unused -> resp);
                })
            .block()
            .status()
            .code();
  }
}

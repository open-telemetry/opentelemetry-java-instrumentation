/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.condition.OS;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

class ReactorNettyHttpClientUsingFromTest extends AbstractReactorNettyHttpClientTest {

  @Override
  HttpClient createHttpClient(boolean readTimeout) {
    return HttpClient.from(TcpClient.create())
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

    // Disable remote connection tests on Windows due to reactor-netty creating extra spans
    if (OS.WINDOWS.isCurrentOs()) {
      optionsBuilder.setTestRemoteConnection(false);
    }
  }
}

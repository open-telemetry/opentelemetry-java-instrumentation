/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.netty.channel.ChannelOption;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import org.junit.jupiter.api.condition.OS;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

class ReactorNettyHttpClientUsingFromTest extends AbstractReactorNettyHttpClientTest {

  @SuppressWarnings("deprecation") // from(TcpClient) is deprecated, but we want to test it anyway
  @Override
  protected HttpClient createHttpClient() {
    int connectionTimeoutMillis = (int) CONNECTION_TIMEOUT.toMillis();
    return HttpClient.from(TcpClient.create())
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMillis)
        .resolver(getAddressResolverGroup())
        .headers(headers -> headers.set(HttpHeaderNames.USER_AGENT, USER_AGENT));
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

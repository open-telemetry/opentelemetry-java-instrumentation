/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.netty.channel.ChannelOption;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
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
}

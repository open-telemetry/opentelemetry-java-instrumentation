/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0

import io.netty.channel.ChannelOption
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.TcpClient

class ReactorNettyHttpClientUsingFromTest extends AbstractReactorNettyHttpClientTest {

  HttpClient createHttpClient() {
    return HttpClient.from(TcpClient.create())
      .tcpConfiguration({ tcpClient ->
        tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
      })
      .resolver(getAddressResolverGroup())
      .headers({ headers -> headers.set(HttpHeaderNames.USER_AGENT, userAgent()) })
  }
}

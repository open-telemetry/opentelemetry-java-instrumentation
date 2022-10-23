/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import java.util.concurrent.TimeUnit
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.TcpClient

class ReactorNettyHttpClientUsingFromTest extends AbstractReactorNettyHttpClientTest {

  HttpClient createHttpClient(boolean readTimeout) {
    return HttpClient.from(TcpClient.create()).tcpConfiguration({ tcpClient ->
      if (readTimeout) {
        tcpClient = tcpClient.doOnConnected({ connection ->
          connection.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        })
      }
      tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
    })
  }
}

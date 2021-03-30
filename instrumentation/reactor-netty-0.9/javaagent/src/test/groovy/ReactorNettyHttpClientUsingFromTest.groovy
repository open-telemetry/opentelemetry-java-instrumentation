/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.TcpClient

class ReactorNettyHttpClientUsingFromTest extends AbstractReactorNettyHttpClientTest {

  HttpClient createHttpClient() {
    return HttpClient.from(TcpClient.create())
  }
}

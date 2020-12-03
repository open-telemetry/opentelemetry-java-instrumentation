/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import play.api.test.TestServer

class PlayAsyncServerTest extends PlayServerTest {
  @Override
  TestServer startServer(int port) {
    def server = AsyncServer.server(port)
    server.start()
    return server
  }
}

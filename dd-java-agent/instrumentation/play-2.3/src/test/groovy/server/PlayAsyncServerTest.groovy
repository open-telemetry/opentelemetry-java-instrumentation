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

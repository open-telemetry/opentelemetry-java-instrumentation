package server

import datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint._
import play.api.mvc.{Action, Handler, Results}
import play.api.test.{FakeApplication, TestServer}

object SyncServer {
  val routes: PartialFunction[(String, String), Handler] = {
    case ("GET", "/success") => Action { request => Results.Status(SUCCESS.getStatus) }
  }

  def server(port: Int): TestServer = {
    TestServer(port, FakeApplication(withRoutes = routes))
  }
}

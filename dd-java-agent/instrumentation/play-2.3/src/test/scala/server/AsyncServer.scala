package server

import datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint._
import play.api.libs.concurrent.Execution.{defaultContext => ec}
import play.api.mvc.{Action, Handler, Results}
import play.api.test.{FakeApplication, TestServer}

import scala.concurrent.Future

object AsyncServer {
  val routes: PartialFunction[(String, String), Handler] = {
    case ("GET", "/success") => Action.async { request => Future.successful(Results.Status(SUCCESS.getStatus).apply(SUCCESS.getBody)) }
    case ("GET", "/redirect") => Action.async { request => Future.successful(Results.Redirect(REDIRECT.getBody, REDIRECT.getStatus)) }
    case ("GET", "/query") => Action.async { result => Future.successful(Results.Status(QUERY_PARAM.getStatus).apply(QUERY_PARAM.getBody)) }
    case ("GET", "/error-status") => Action.async { result => Future {
      throw new RuntimeException(ERROR.getBody)
    }(ec)
    }
    case ("GET", "/exception") => Action.async { result => Future {
      throw new RuntimeException(ERROR.getBody)
    }(ec)
    }
  }

  def server(port: Int): TestServer = {
    TestServer(port, FakeApplication(withGlobal = Some(new Settings()), withRoutes = routes))
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint._
import play.api.mvc.{Action, Handler, Results}
import play.api.test.{FakeApplication, TestServer}

object SyncServer {
  val routes: PartialFunction[(String, String), Handler] = {
    case ("GET", "/success") =>
      Action { request =>
        HttpServerTest.controller(
          SUCCESS,
          new ControllerClosureAdapter(
            Results.Status(SUCCESS.getStatus).apply(SUCCESS.getBody)
          )
        )
      }
    case ("GET", "/redirect") =>
      Action { request =>
        HttpServerTest.controller(
          REDIRECT,
          new ControllerClosureAdapter(
            Results.Redirect(REDIRECT.getBody, REDIRECT.getStatus)
          )
        )
      }
    case ("GET", "/query") =>
      Action { request =>
        HttpServerTest.controller(
          QUERY_PARAM,
          new ControllerClosureAdapter(
            Results.Status(QUERY_PARAM.getStatus).apply(QUERY_PARAM.getBody)
          )
        )
      }
    case ("GET", "/error-status") =>
      Action { request =>
        HttpServerTest.controller(
          ERROR,
          new ControllerClosureAdapter(
            Results.Status(ERROR.getStatus).apply(ERROR.getBody)
          )
        )
      }
    case ("GET", "/exception") =>
      Action { request =>
        HttpServerTest.controller(EXCEPTION, new BlockClosureAdapter(() => {
          throw new Exception(EXCEPTION.getBody)
        }))
      }
  }

  def server(port: Int): TestServer = {
    TestServer(
      port,
      FakeApplication(withGlobal = Some(new Settings()), withRoutes = routes)
    )
  }
}

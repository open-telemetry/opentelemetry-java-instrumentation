/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint._
import play.api.mvc.{Action, Handler, Results}
import play.api.test.{FakeApplication, TestServer}

import scala.concurrent.Future

object AsyncServer {
  val routes: PartialFunction[(String, String), Handler] = {
    case ("GET", "/success") =>
      Action.async { request =>
        HttpServerTest.controller(
          SUCCESS,
          new AsyncControllerClosureAdapter(
            Future.successful(
              Results.Status(SUCCESS.getStatus).apply(SUCCESS.getBody)
            )
          )
        )
      }
    case ("GET", "/redirect") =>
      Action.async { request =>
        HttpServerTest.controller(
          REDIRECT,
          new AsyncControllerClosureAdapter(
            Future.successful(
              Results.Redirect(REDIRECT.getBody, REDIRECT.getStatus)
            )
          )
        )
      }
    case ("GET", "/query") =>
      Action.async { result =>
        HttpServerTest.controller(
          QUERY_PARAM,
          new AsyncControllerClosureAdapter(
            Future.successful(
              Results.Status(QUERY_PARAM.getStatus).apply(QUERY_PARAM.getBody)
            )
          )
        )
      }
    case ("GET", "/error-status") =>
      Action.async { result =>
        HttpServerTest.controller(
          ERROR,
          new AsyncControllerClosureAdapter(
            Future
              .successful(Results.Status(ERROR.getStatus).apply(ERROR.getBody))
          )
        )
      }
    case ("GET", "/exception") =>
      Action.async { result =>
        HttpServerTest.controller(
          EXCEPTION,
          new AsyncBlockClosureAdapter(() => {
            throw new Exception(EXCEPTION.getBody)
          })
        )
      }
  }

  def server(port: Int): TestServer = {
    TestServer(
      port,
      FakeApplication(withGlobal = Some(new Settings()), withRoutes = routes)
    )
  }
}

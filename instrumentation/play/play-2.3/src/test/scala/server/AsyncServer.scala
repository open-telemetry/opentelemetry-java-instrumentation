/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package server

import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint._
import play.api.mvc.{Action, Handler, Results}
import play.api.test.{FakeApplication, TestServer}

import scala.concurrent.Future

object AsyncServer {
  val routes: PartialFunction[(String, String), Handler] = {
    case ("GET", "/success") => Action.async { request => HttpServerTest.controller(SUCCESS, new AsyncControllerClosureAdapter(Future.successful(Results.Status(SUCCESS.getStatus).apply(SUCCESS.getBody)))) }
    case ("GET", "/redirect") => Action.async { request => HttpServerTest.controller(REDIRECT, new AsyncControllerClosureAdapter(Future.successful(Results.Redirect(REDIRECT.getBody, REDIRECT.getStatus)))) }
    case ("GET", "/query") => Action.async { result => HttpServerTest.controller(QUERY_PARAM, new AsyncControllerClosureAdapter(Future.successful(Results.Status(QUERY_PARAM.getStatus).apply(QUERY_PARAM.getBody)))) }
    case ("GET", "/error-status") => Action.async { result => HttpServerTest.controller(ERROR, new AsyncControllerClosureAdapter(Future.successful(Results.Status(ERROR.getStatus).apply(ERROR.getBody)))) }
    case ("GET", "/exception") => Action.async { result =>
      HttpServerTest.controller(EXCEPTION, new AsyncBlockClosureAdapter(() => {
        throw new Exception(EXCEPTION.getBody)
      }))
    }
  }

  def server(port: Int): TestServer = {
    TestServer(port, FakeApplication(withGlobal = Some(new Settings()), withRoutes = routes))
  }
}

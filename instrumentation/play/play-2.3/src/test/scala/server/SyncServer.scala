/*
 * Copyright 2020, OpenTelemetry Authors
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

object SyncServer {
  val routes: PartialFunction[(String, String), Handler] = {
    case ("GET", "/success") => Action { request =>
      HttpServerTest.controller(SUCCESS, new ControllerClosureAdapter(Results.Status(SUCCESS.getStatus).apply(SUCCESS.getBody)))
    }
    case ("GET", "/redirect") => Action { request =>
      HttpServerTest.controller(REDIRECT, new ControllerClosureAdapter(Results.Redirect(REDIRECT.getBody, REDIRECT.getStatus)))
    }
    case ("GET", "/query") => Action { request =>
      HttpServerTest.controller(QUERY_PARAM, new ControllerClosureAdapter(Results.Status(QUERY_PARAM.getStatus).apply(QUERY_PARAM.getBody)))
    }
    case ("GET", "/error-status") => Action { request =>
      HttpServerTest.controller(ERROR, new ControllerClosureAdapter(Results.Status(ERROR.getStatus).apply(ERROR.getBody)))
    }
    case ("GET", "/exception") => Action { request =>
      HttpServerTest.controller(EXCEPTION, new BlockClosureAdapter(() => {
        throw new Exception(EXCEPTION.getBody)
      }))
    }
  }

  def server(port: Int): TestServer = {
    TestServer(port, FakeApplication(withGlobal = Some(new Settings()), withRoutes = routes))
  }
}

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

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.Controller
import com.twitter.util.Future
import groovy.lang.Closure
import io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint._
import io.opentelemetry.auto.test.base.HttpServerTest.controller

class FinatraController extends Controller {
  any(SUCCESS.getPath) { request: Request =>
    controller(SUCCESS, new Closure[Response](null) {
      override def call(): Response = {
        response.ok(SUCCESS.getBody)
      }
    })
  }

  any(ERROR.getPath) { request: Request =>
    controller(ERROR, new Closure[Response](null) {
      override def call(): Response = {
        response.internalServerError(ERROR.getBody)
      }
    })
  }

  any(QUERY_PARAM.getPath) { request: Request =>
    controller(QUERY_PARAM, new Closure[Response](null) {
      override def call(): Response = {
        response.ok(QUERY_PARAM.getBody)
      }
    })
  }

  any(EXCEPTION.getPath) { request: Request =>
    controller(EXCEPTION, new Closure[Future[Response]](null) {
      override def call(): Future[Response] = {
        throw new Exception(EXCEPTION.getBody)
      }
    })
  }

  any(REDIRECT.getPath) { request: Request =>
    controller(REDIRECT, new Closure[Response](null) {
      override def call(): Response = {
        response.found.location(REDIRECT.getBody)
      }
    })
  }

  any("/path/:id/param") { request: Request =>
    controller(PATH_PARAM, new Closure[Response](null) {
      override def call(): Response = {
        response.ok(request.params("id"))
      }
    })
  }
}

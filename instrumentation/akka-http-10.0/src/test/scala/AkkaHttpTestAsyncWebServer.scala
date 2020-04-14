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
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import groovy.lang.Closure
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint._

import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object AkkaHttpTestAsyncWebServer {
  implicit val system: ActorSystem = ActorSystem("my-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  val asyncHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(GET, uri: Uri, _, _, _) =>
      Future {
        val endpoint = HttpServerTest.ServerEndpoint.forPath(uri.path.toString())
        HttpServerTest.controller(endpoint, new Closure[HttpResponse](()) {
          def doCall(): HttpResponse = {
            val resp = HttpResponse(status = endpoint.getStatus) //.withHeaders(headers.Type)resp.contentType = "text/plain"
            endpoint match {
              case SUCCESS => resp.withEntity(endpoint.getBody)
              case QUERY_PARAM => resp.withEntity(uri.queryString().orNull)
              case REDIRECT => resp.withHeaders(headers.Location(endpoint.getBody))
              case ERROR => resp.withEntity(endpoint.getBody)
              case EXCEPTION => throw new Exception(endpoint.getBody)
              case _ => HttpResponse(status = NOT_FOUND.getStatus).withEntity(NOT_FOUND.getBody)
            }
          }
        })
      }
  }

  private var binding: ServerBinding = _

  def start(port: Int): Unit = synchronized {
    if (null == binding) {
      import scala.concurrent.duration._
      binding = Await.result(Http().bindAndHandleAsync(asyncHandler, "localhost", port), 10 seconds)
    }
  }

  def stop(): Unit = synchronized {
    if (null != binding) {
      binding.unbind()
      system.terminate()
      binding = null
    }
  }
}

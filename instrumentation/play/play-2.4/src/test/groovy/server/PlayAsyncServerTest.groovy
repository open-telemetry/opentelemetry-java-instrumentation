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

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import play.libs.HttpExecution
import play.mvc.Results
import play.routing.RoutingDsl
import play.server.Server
import play.libs.F

class PlayAsyncServerTest extends PlayServerTest {
  @Override
  Server startServer(int port) {
    def router =
      new RoutingDsl()
        .GET(SUCCESS.getPath()).routeAsync(
        new F.Function0<F.Promise<Results.Status>>() {
          @Override
          F.Promise<Results.Status> apply() throws Throwable {
            return F.Promise.promise({
              controller(SUCCESS) {
                Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
              }
            }, HttpExecution.defaultContext())
          }
        })
        .GET(QUERY_PARAM.getPath()).routeAsync(
        new F.Function0<F.Promise<Results.Status>>() {
          @Override
          F.Promise<Results.Status> apply() throws Throwable {
            return F.Promise.promise({
              controller(QUERY_PARAM) {
                Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())
              }
            }, HttpExecution.defaultContext())
          }
        })
        .GET(REDIRECT.getPath()).routeAsync(
        new F.Function0<F.Promise<Results.Status>>() {
          @Override
          F.Promise<Results.Status> apply() throws Throwable {
            return F.Promise.promise({
              controller(REDIRECT) {
                Results.found(REDIRECT.getBody())
              }
            }, HttpExecution.defaultContext())
          }
        })
        .GET(ERROR.getPath()).routeAsync(
        new F.Function0<F.Promise<Results.Status>>() {
          @Override
          F.Promise<Results.Status> apply() throws Throwable {
            return F.Promise.promise({
              controller(ERROR) {
                Results.status(ERROR.getStatus(), ERROR.getBody())
              }
            }, HttpExecution.defaultContext())
          }
        })
        .GET(EXCEPTION.getPath()).routeAsync(
        new F.Function0<F.Promise<Results.Status>>() {
          @Override
          F.Promise<Results.Status> apply() throws Throwable {
            return F.Promise.promise({
              controller(EXCEPTION) {
                throw new Exception(EXCEPTION.getBody())
              }
            }, HttpExecution.defaultContext())
          }
        })


    return Server.forRouter(router.build(), port)
  }
}

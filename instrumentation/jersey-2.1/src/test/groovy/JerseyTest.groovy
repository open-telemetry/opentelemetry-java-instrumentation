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
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.trace.Span
import org.glassfish.grizzly.http.server.HttpServer

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class JerseyTest extends HttpServerTest<HttpServer> {
  static {
    ConfigUtils.updateConfig {
      System.setProperty("ota.integration.jersey.enabled", "true")
      //Make sure that Grizzly instrumentation is disabled so that Jersey SERVER spans are created
      System.setProperty("ota.integration.grizzly.enabled", "false")
    }
  }

  def specCleanup() {
    ConfigUtils.updateConfig {
      System.clearProperty("ota.integration.grizzly.enabled")
      System.clearProperty("ota.integration.jersey.enabled")
    }
  }

  @Override
  HttpServer startServer(int port) {
    return JerseyServer.startServer(port)
  }


  @Override
  void stopServer(HttpServer server) {
    server.stop()
  }

  //This test overrides server span check from above because Jersey cannot provide {code net.*} attributes
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName expectedOperationName(method)
      spanKind Span.Kind.SERVER // can't use static import because of SERVER type parameter
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "$Tags.HTTP_URL" { it == "${endpoint.resolve(address)}" || it == "${endpoint.resolveWithoutFragment(address)}" }
        "$Tags.HTTP_METHOD" method
        "$Tags.HTTP_STATUS" endpoint.status
        if (endpoint.query) {
          "$MoreTags.HTTP_QUERY" endpoint.query
        }
        //TODO why HttpServerTest does not check these tags?
        if (endpoint.errored) {
          "error.msg" {
            it == null || it == EXCEPTION.body
          }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
      }
    }
  }

}

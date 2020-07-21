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

package test.filter

import io.opentelemetry.auto.instrumentation.api.MoreAttributes
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes
import okhttp3.Response
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import test.boot.SecurityConfig

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.trace.Span.Kind.INTERNAL
import static io.opentelemetry.trace.Span.Kind.SERVER
import static java.util.Collections.singletonMap

class ServletFilterTest extends HttpServerTest<ConfigurableApplicationContext> {

  @Override
  ConfigurableApplicationContext startServer(int port) {
    def app = new SpringApplication(FilteredAppConfig, SecurityConfig)
    app.setDefaultProperties(singletonMap("server.port", port))
    def context = app.run()
    return context
  }

  @Override
  void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close()
  }

  @Override
  boolean hasHandlerSpan() {
    false
  }

  @Override
  boolean hasErrorPageSpans(ServerEndpoint endpoint) {
    endpoint == ERROR || endpoint == EXCEPTION
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  boolean testExceptionBody() {
    false
  }

  @Override
  boolean testNotFound() {
    // FIXME: the instrumentation adds an extra controller span which is not consistent.
    // Fix tests or remove extra span.
    false
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName "TestController.${endpoint.name().toLowerCase()}"
      spanKind INTERNAL
      errored endpoint == EXCEPTION
      childOf((SpanData) parent)
      attributes {
        if (endpoint == EXCEPTION) {
          errorTags(Exception, EXCEPTION.body)
        }
      }
    }
  }

  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS, Response response = null) {
    trace.span(index) {
      operationName endpoint == PATH_PARAM ? "/path/{id}/param" : endpoint.resolvePath(address).path
      spanKind SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      attributes {
        "${SemanticAttributes.NET_PEER_IP.key()}" { it == null || it == "127.0.0.1" } // Optional
        "${SemanticAttributes.NET_PEER_PORT.key()}" Long
        "${SemanticAttributes.HTTP_URL.key()}" { it == "${endpoint.resolve(address)}" || it == "${endpoint.resolveWithoutFragment(address)}" }
        "${SemanticAttributes.HTTP_METHOD.key()}" method
        "${SemanticAttributes.HTTP_STATUS_CODE.key()}" endpoint.status
        // exception bodies are not yet recorded
        "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH.key()}" { "${response?.body()?.contentLength() ?: 0}" || endpoint == EXCEPTION }
        "servlet.path" endpoint.path
        "servlet.context" ""
        if (endpoint.errored) {
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        if (endpoint.query) {
          "$MoreAttributes.HTTP_QUERY" endpoint.query
        }
      }
    }
  }

  @Override
  void errorPageSpans(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName "BasicErrorController.error"
      spanKind INTERNAL
      errored false
      childOf((SpanData) parent)
      attributes {
      }
    }
  }
}

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

import io.opentelemetry.auto.instrumentation.api.MoreAttributes
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes
import ratpack.error.ServerErrorHandler
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.handling.Context
import ratpack.test.embed.EmbeddedApp

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static io.opentelemetry.trace.Span.Kind.INTERNAL
import static io.opentelemetry.trace.Span.Kind.SERVER

class RatpackHttpServerTest extends HttpServerTest<EmbeddedApp> {

  @Override
  EmbeddedApp startServer(int bindPort) {
    def ratpack = GroovyEmbeddedApp.ratpack {
      serverConfig {
        port bindPort
        address InetAddress.getByName('localhost')
      }
      bindings {
        bind TestErrorHandler
      }
      handlers {
        prefix(SUCCESS.rawPath()) {
          all {
            controller(SUCCESS) {
              context.response.status(SUCCESS.status).send(SUCCESS.body)
            }
          }
        }
        prefix(QUERY_PARAM.rawPath()) {
          all {
            controller(QUERY_PARAM) {
              context.response.status(QUERY_PARAM.status).send(request.query)
            }
          }
        }
        prefix(REDIRECT.rawPath()) {
          all {
            controller(REDIRECT) {
              context.redirect(REDIRECT.body)
            }
          }
        }
        prefix(ERROR.rawPath()) {
          all {
            controller(ERROR) {
              context.response.status(ERROR.status).send(ERROR.body)
            }
          }
        }
        prefix(EXCEPTION.rawPath()) {
          all {
            controller(EXCEPTION) {
              throw new Exception(EXCEPTION.body)
            }
          }
        }
        prefix("path/:id/param") {
          all {
            controller(PATH_PARAM) {
              context.response.status(PATH_PARAM.status).send(pathTokens.id)
            }
          }
        }
      }
    }
    ratpack.server.start()

    assert ratpack.address.port == bindPort
    return ratpack
  }

  static class TestErrorHandler implements ServerErrorHandler {
    @Override
    void error(Context context, Throwable throwable) throws Exception {
      context.response.status(500).send(throwable.message)
    }
  }

  @Override
  void stopServer(EmbeddedApp server) {
    server.close()
  }

  @Override
  boolean hasHandlerSpan() {
    true
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName endpoint.status == 404 ? "/" : endpoint == PATH_PARAM ? "/path/:id/param" : endpoint.path
      spanKind INTERNAL
      errored endpoint == EXCEPTION
      childOf((SpanData) parent)
      attributes {
        if (endpoint == EXCEPTION) {
          errorAttributes(Exception, EXCEPTION.body)
        }
      }
    }
  }

  @Override
  String expectedServerSpanName(String method, ServerEndpoint endpoint) {
    return endpoint.status == 404 ? "/" : endpoint == PATH_PARAM ? "/path/:id/param" : endpoint.path
  }
}

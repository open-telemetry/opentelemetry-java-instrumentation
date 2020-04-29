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
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.OkHttpUtils
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.path.PathBinding

import static io.opentelemetry.trace.Span.Kind.INTERNAL
import static io.opentelemetry.trace.Span.Kind.SERVER

class RatpackOtherTest extends AgentTestRunner {

  OkHttpClient client = OkHttpUtils.client()

  def "test bindings for #path"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        prefix("a") {
          all {
            context.render(context.get(PathBinding).description)
          }
        }
        prefix("b/::\\d+") {
          all {
            context.render(context.get(PathBinding).description)
          }
        }
        prefix("c/:val?") {
          all {
            context.render(context.get(PathBinding).description)
          }
        }
        prefix("d/:val") {
          all {
            context.render(context.get(PathBinding).description)
          }
        }
        prefix("e/:val?:\\d+") {
          all {
            context.render(context.get(PathBinding).description)
          }
        }
        prefix("f/:val:\\d+") {
          all {
            context.render(context.get(PathBinding).description)
          }
        }
      }
    }
    def request = new Request.Builder()
      .url(HttpUrl.get(app.address).newBuilder().addPathSegments(path).build())
      .get()
      .build()

    when:
    def resp = client.newCall(request).execute()

    then:
    resp.code() == 200
    resp.body.string() == route

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "/$route"
          spanKind SERVER
          parent()
          errored false
          tags {
            "$MoreTags.NET_PEER_IP" "127.0.0.1"
            "$MoreTags.NET_PEER_PORT" Long
            "$Tags.HTTP_URL" "${app.address.resolve(path)}"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
          }
        }
        span(1) {
          operationName "/$route"
          spanKind INTERNAL
          childOf span(0)
          errored false
          tags {
            "$MoreTags.NET_PEER_IP" "127.0.0.1"
            "$MoreTags.NET_PEER_PORT" Long
            "$Tags.HTTP_URL" "${app.address.resolve(path)}"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
          }
        }
      }
    }

    where:
    path    | route
    "a"     | "a"
    "b/123" | "b/::\\d+"
    "c"     | "c/:val?"
    "c/123" | "c/:val?"
    "c/foo" | "c/:val?"
    "d/123" | "d/:val"
    "d/foo" | "d/:val"
    "e"     | "e/:val?:\\d+"
    "e/123" | "e/:val?:\\d+"
    "e/foo" | "e/:val?:\\d+"
    "f/123" | "f/:val:\\d+"
  }
}

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.path.PathBinding

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
          resourceName "GET /$route"
          serviceName "unnamed-java-app"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          errored false
          tags {
            "$Tags.COMPONENT" "netty"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "${app.address.resolve(path)}"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
            defaultTags()
          }
        }
        span(1) {
          resourceName "GET /$route"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(0))
          errored false
          tags {
            "$Tags.COMPONENT" "ratpack"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "${app.address.resolve(path)}"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
            defaultTags()
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

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.context.TraceScope
import io.opentracing.Scope
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ratpack.exec.Promise
import ratpack.exec.util.ParallelBatch
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.path.PathBinding
import ratpack.test.exec.ExecHarness

import java.util.concurrent.CountDownLatch

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
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "${app.address.resolve(path)}"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
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
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "${app.address.resolve(path)}"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_PORT.key" Integer
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

  def "forked executions inherit parent scope"() {
    when:
    def result = ExecHarness.yieldSingle({}, {
      final Scope scope =
        GlobalTracer.get()
          .buildSpan("ratpack.exec-test")
          .withTag(DDTags.RESOURCE_NAME, "INSIDE-TEST")
          .startActive(true)

      ((TraceScope) scope).setAsyncPropagation(true)
      scope.span().setBaggageItem("test-baggage", "foo")
      ParallelBatch.of(testPromise(), testPromise())
        .yield()
        .map({ now ->
          // close the scope now that we got the baggage inside the promises
          scope.close()
          return now
        })
    })

    then:
    result.valueOrThrow == ["foo", "foo"]
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          resourceName "INSIDE-TEST"
          serviceName "unnamed-java-app"
          operationName "ratpack.exec-test"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
      }
    }
  }

  // returns a promise that contains the active scope's "test-baggage" baggage
  Promise<String> testPromise(CountDownLatch latch = null) {
    Promise.sync {
      latch?.await()
      Scope tracerScope = GlobalTracer.get().scopeManager().active()
      return tracerScope?.span()?.getBaggageItem("test-baggage")
    }
  }
}

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.context.TraceScope
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ratpack.exec.Promise
import ratpack.exec.util.ParallelBatch
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.http.HttpUrlBuilder
import ratpack.http.client.HttpClient
import ratpack.path.PathBinding
import ratpack.test.exec.ExecHarness

class RatpackTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.ratpack.enabled", "true")
  }

  OkHttpClient client = OkHttpUtils.client()


  def "test path call"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        get {
          context.render("success")
        }
      }
    }
    def request = new Request.Builder()
      .url(app.address.toURL())
      .get()
      .build()

    when:
    def resp = client.newCall(request).execute()

    then:
    resp.code() == 200
    resp.body.string() == "success"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          resourceName "GET /"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "/"
            defaultTags()
          }
        }
      }
    }
  }

  def "test path with bindings call"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        prefix(":foo/:bar?") {
          get("baz") { ctx ->
            context.render(ctx.get(PathBinding).description)
          }
        }
      }
    }
    def request = new Request.Builder()
      .url(HttpUrl.get(app.address).newBuilder().addPathSegments("a/b/baz").build())
      .get()
      .build()

    when:
    def resp = client.newCall(request).execute()

    then:
    resp.code() == 200
    resp.body.string() == ":foo/:bar?/baz"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          resourceName "GET /:foo/:bar?/baz"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "/a/b/baz"
            defaultTags()
          }
        }
      }
    }
  }

  def "test error response"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        get {
          context.render(Promise.sync {
            return "fail " + 0 / 0
          })
        }
      }
    }
    def request = new Request.Builder()
      .url(app.address.toURL())
      .get()
      .build()
    when:
    def resp = client.newCall(request).execute()
    then:
    resp.code() == 500

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          resourceName "GET /"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          errored true
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "/"
            "error" true
//            errorTags(Exception, String) // TODO: find out how to get throwable in instrumentation
            defaultTags()
          }
        }
      }
    }
  }

  def "test path call using ratpack http client"() {
    setup:

    def external = GroovyEmbeddedApp.ratpack {
      handlers {
        get("nested") {
          context.render("succ")
        }
        get("nested2") {
          context.render("ess")
        }
      }
    }

    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        get { HttpClient httpClient ->
          // 1st internal http client call to nested
          httpClient.get(HttpUrlBuilder.base(external.address).path("nested").build())
            .map { it.body.text }
            .flatMap { t ->
            // make a 2nd http request and concatenate the two bodies together
            httpClient.get(HttpUrlBuilder.base(external.address).path("nested2").build()) map { t + it.body.text }
          }
          .then {
            context.render(it)
          }
        }
      }
    }
    def request = new Request.Builder()
      .url(app.address.toURL())
      .get()
      .build()

    when:
    def resp = client.newCall(request).execute()

    then:
    resp.code() == 200
    resp.body().string() == "success"

    // 3rd is the three traces, ratpack, http client 2 and http client 1
    // 2nd is nested2 from the external server (the result of the 2nd internal http client call)
    // 1st is nested from the external server (the result of the 1st internal http client call)
    assertTraces(3) {
      // simulated external system, first call
      trace(0, 1) {
        span(0) {
          resourceName "GET /nested"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(trace(2).get(2))
          errored false
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "/nested"
            defaultTags(true)
          }
        }
      }
      // simulated external system, second call
      trace(1, 1) {
        span(0) {
          resourceName "GET /nested2"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(trace(2).get(1))
          errored false
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "/nested2"
            defaultTags(true)
          }
        }
      }
      trace(2, 3) {
        // main app span that processed the request from OKHTTP request
        span(0) {
          resourceName "GET /"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "/"
            defaultTags()
          }
        }
        // Second http client call that receives the 'ess' of Success
        span(1) {
          resourceName "GET /?"
          serviceName "unnamed-java-app"
          operationName "ratpack.client-request"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf(span(0))
          errored false
          tags {
            "$Tags.COMPONENT.key" "ratpack-httpclient"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "${external.address}nested2"
            defaultTags()
          }
        }
        // First http client call that receives the 'Succ' of Success
        span(2) {
          resourceName "GET /nested"
          serviceName "unnamed-java-app"
          operationName "ratpack.client-request"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf(span(0))
          errored false
          tags {
            "$Tags.COMPONENT.key" "ratpack-httpclient"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "${external.address}nested"
            defaultTags()
          }
        }
      }
    }
  }

  def "test forked path call and start span in handler (#startSpanInHandler)"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        get {
          final Scope scope = !startSpanInHandler ? GlobalTracer.get().scopeManager().active() :
            GlobalTracer.get()
              .buildSpan("ratpack.exec-test")
              .withTag(DDTags.RESOURCE_NAME, "INSIDE-TEST")
              .startActive(false)

          if (startSpanInHandler) {
            ((TraceScope) scope).setAsyncPropagation(true)
          }
          scope.span().setBaggageItem("test-baggage", "foo")

          final Span startedSpan = startSpanInHandler ? scope.span() : null
          if (startSpanInHandler) {
            scope.close()
            context.onClose {
                startedSpan.finish()
            }
          }

          context.render(testPromise().fork())
        }
      }
    }
    def request = new Request.Builder()
      .url(app.address.toURL())
      .get()
      .build()

    when:
    def resp = client.newCall(request).execute()

    then:
    resp.code() == 200
    resp.body().string() == "foo"

    assertTraces(1) {
      trace(0, (startSpanInHandler ? 2 : 1)) {
        span(startSpanInHandler ? 1 : 0) {
          resourceName "GET /"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "/"
            defaultTags()
          }
        }
        if (startSpanInHandler) {
          span(0) {
            resourceName "INSIDE-TEST"
            serviceName "unnamed-java-app"
            operationName "ratpack.exec-test"
            spanType DDSpanTypes.HTTP_SERVER
            childOf(span(1))
            errored false
            tags {
              "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
              defaultTags()
            }
          }
        }
      }
    }

    where:
    startSpanInHandler << [true, false]
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
  Promise<String> testPromise() {
    Promise.sync {
      Scope tracerScope = GlobalTracer.get().scopeManager().active()
      return tracerScope.span().getBaggageItem("test-baggage")
    }
  }
}

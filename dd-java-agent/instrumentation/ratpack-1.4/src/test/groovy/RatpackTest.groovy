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

<<<<<<< HEAD
    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().serviceName == "unnamed-java-app"
    span.context().operationName == "ratpack.handler"
    span.context().resourceName == "GET /"
    span.context().tags["component"] == "ratpack"
    span.context().spanType == DDSpanTypes.HTTP_SERVER
    !span.context().getErrorFlag()
    span.context().tags["http.url"] == "/"
    span.context().tags["http.method"] == "GET"
    span.context().tags["span.kind"] == "server"
    span.context().tags["http.status_code"] == 200
    span.context().tags["thread.name"] != null
    span.context().tags["thread.id"] != null
=======
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
            "$Tags.COMPONENT.key" "handler"
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
>>>>>>> 1bfa7e47... Refactor Ratpack
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

<<<<<<< HEAD
    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().serviceName == "unnamed-java-app"
    span.context().operationName == "ratpack.handler"
    span.context().resourceName == "GET /:foo/:bar?/baz"
    span.context().tags["component"] == "ratpack"
    span.context().spanType == DDSpanTypes.HTTP_SERVER
    !span.context().getErrorFlag()
    span.context().tags["http.url"] == "/a/b/baz"
    span.context().tags["http.method"] == "GET"
    span.context().tags["span.kind"] == "server"
    span.context().tags["http.status_code"] == 200
    span.context().tags["thread.name"] != null
    span.context().tags["thread.id"] != null
=======
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
            "$Tags.COMPONENT.key" "handler"
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
>>>>>>> 1bfa7e47... Refactor Ratpack
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

<<<<<<< HEAD
    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().getErrorFlag()
    span.context().serviceName == "unnamed-java-app"
    span.context().operationName == "ratpack.handler"
    span.context().resourceName == "GET /"
    span.context().tags["component"] == "ratpack"
    span.context().spanType == DDSpanTypes.HTTP_SERVER
    span.context().tags["http.url"] == "/"
    span.context().tags["http.method"] == "GET"
    span.context().tags["span.kind"] == "server"
    span.context().tags["http.status_code"] == 500
    span.context().tags["thread.name"] != null
    span.context().tags["thread.id"] != null
=======
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
            "$Tags.COMPONENT.key" "handler"
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
>>>>>>> 1bfa7e47... Refactor Ratpack
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
<<<<<<< HEAD
    TEST_WRITER.size() == 3
    def trace = TEST_WRITER.get(2)
    trace.size() == 3
    def span = trace[0]

    span.context().serviceName == "unnamed-java-app"
    span.context().operationName == "ratpack.handler"
    span.context().resourceName == "GET /"
    span.context().tags["component"] == "ratpack"
    span.context().spanType == DDSpanTypes.HTTP_SERVER
    !span.context().getErrorFlag()
    span.context().tags["http.url"] == "/"
    span.context().tags["http.method"] == "GET"
    span.context().tags["span.kind"] == "server"
    span.context().tags["http.status_code"] == 200
    span.context().tags["thread.name"] != null
    span.context().tags["thread.id"] != null

    def clientTrace1 = trace[1] // Second http client call that receives the 'ess' of Success

    clientTrace1.context().serviceName == "unnamed-java-app"
    clientTrace1.context().operationName == "ratpack.client-request"
    clientTrace1.context().tags["component"] == "ratpack-httpclient"
    !clientTrace1.context().getErrorFlag()
    clientTrace1.context().tags["http.url"] == "${external.address}nested2"
    clientTrace1.context().tags["http.method"] == "GET"
    clientTrace1.context().tags["span.kind"] == "client"
    clientTrace1.context().tags["http.status_code"] == 200
    clientTrace1.context().tags["thread.name"] != null
    clientTrace1.context().tags["thread.id"] != null
=======
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
            "$Tags.COMPONENT.key" "handler"
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
            "$Tags.COMPONENT.key" "handler"
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
            "$Tags.COMPONENT.key" "handler"
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
            "$Tags.COMPONENT.key" "httpclient"
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
            "$Tags.COMPONENT.key" "httpclient"
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
>>>>>>> 1bfa7e47... Refactor Ratpack

          final Scope scope = !startSpanInHandler ? GlobalTracer.get().scopeManager().active() :
            GlobalTracer.get()
              .buildSpan("ratpack.exec-test")
              .withTag(DDTags.RESOURCE_NAME, "INSIDE-TEST")
              .startActive(true)

<<<<<<< HEAD
    clientTrace2.context().serviceName == "unnamed-java-app"
    clientTrace2.context().operationName == "ratpack.client-request"
    clientTrace1.context().tags["component"] == "ratpack-httpclient"
    !clientTrace2.context().getErrorFlag()
    clientTrace2.context().tags["http.url"] == "${external.address}nested"
    clientTrace2.context().tags["http.method"] == "GET"
    clientTrace2.context().tags["span.kind"] == "client"
    clientTrace2.context().tags["http.status_code"] == 200
    clientTrace2.context().tags["thread.name"] != null
    clientTrace2.context().tags["thread.id"] != null
=======
          ((TraceScope) scope).setAsyncPropagation(true)
          scope.span().setBaggageItem("test-baggage", "foo")
          context.render(testPromise(startSpanInHandler).fork())
        }
      }
    }
    def request = new Request.Builder()
      .url(app.address.toURL())
      .get()
      .build()
>>>>>>> 1bfa7e47... Refactor Ratpack

    when:
    def resp = client.newCall(request).execute()

<<<<<<< HEAD
    nestedSpan.context().serviceName == "unnamed-java-app"
    nestedSpan.context().operationName == "ratpack.handler"
    nestedSpan.context().resourceName == "GET /nested2"
    nestedSpan.context().tags["component"] == "ratpack"
    nestedSpan.context().spanType == DDSpanTypes.HTTP_SERVER
    !nestedSpan.context().getErrorFlag()
    nestedSpan.context().tags["http.url"] == "/nested2"
    nestedSpan.context().tags["http.method"] == "GET"
    nestedSpan.context().tags["span.kind"] == "server"
    nestedSpan.context().tags["http.status_code"] == 200
    nestedSpan.context().tags["thread.name"] != null
    nestedSpan.context().tags["thread.id"] != null
=======
    then:
    resp.code() == 200
    resp.body().string() == "foo"
>>>>>>> 1bfa7e47... Refactor Ratpack

    assertTraces(1) {
      trace(0, (startSpanInHandler ? 2 : 1)) {
        span(0) {
          resourceName "GET /"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "handler"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "/"
            defaultTags()
          }
        }
        if (startSpanInHandler) {
          span(1) {
            resourceName "INSIDE-TEST"
            serviceName "unnamed-java-app"
            operationName "ratpack.exec-test"
            spanType DDSpanTypes.HTTP_SERVER
            childOf(span(0))
            errored false
            tags {
              "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
              defaultTags()
            }
          }
        }
      }
    }

<<<<<<< HEAD
    nestedSpan2.context().serviceName == "unnamed-java-app"
    nestedSpan2.context().operationName == "ratpack.handler"
    nestedSpan2.context().resourceName == "GET /nested"
    nestedSpan2.context().tags["component"] == "ratpack"
    nestedSpan2.context().spanType == DDSpanTypes.HTTP_SERVER
    !nestedSpan2.context().getErrorFlag()
    nestedSpan2.context().tags["http.url"] == "/nested"
    nestedSpan2.context().tags["http.method"] == "GET"
    nestedSpan2.context().tags["span.kind"] == "server"
    nestedSpan2.context().tags["http.status_code"] == 200
    nestedSpan2.context().tags["thread.name"] != null
    nestedSpan2.context().tags["thread.id"] != null
=======
    where:
    startSpanInHandler << [true, false]
>>>>>>> 1bfa7e47... Refactor Ratpack
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
      ParallelBatch.of(testPromise(false), testPromise(false))
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
  // will close an active scope if closeSpan is set to true
  Promise<String> testPromise(boolean closeSpan = true) {
    Promise.sync {
      Scope tracerScope = GlobalTracer.get().scopeManager().active()
      String res = tracerScope.span().getBaggageItem("test-baggage")
      if (closeSpan) {
        tracerScope.close()
      }
      return res
    }
  }
}

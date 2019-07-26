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
import ratpack.handling.internal.HandlerException
import ratpack.http.HttpUrlBuilder
import ratpack.http.client.HttpClient
import ratpack.path.PathBinding
import ratpack.test.exec.ExecHarness

import java.util.concurrent.CountDownLatch
import java.util.regex.Pattern

import static datadog.trace.agent.test.server.http.TestHttpServer.distributedRequestTrace
import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.PortUtils.UNUSABLE_PORT

class RatpackTest extends AgentTestRunner {

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

  def "test handler error response"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        prefix("handler-error") {
          all {
            0 / 0
          }
        }
      }
    }
    def request = new Request.Builder()
      .url(app.address.resolve("/handler-error?query=param").toURL())
      .get()
      .build()
    when:
    def resp = client.newCall(request).execute()
    then:
    resp.code() == 500

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          resourceName "GET /handler-error"
          serviceName "unnamed-java-app"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          errored true
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "${app.address.resolve('handler-error')}"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            errorTags(ArithmeticException, Pattern.compile("Division( is)? undefined"))
            defaultTags()
          }
        }
        span(1) {
          resourceName "GET /handler-error"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(0))
          errored true
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "${app.address.resolve('handler-error')}"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_PORT.key" Integer
            errorTags(HandlerException, Pattern.compile("java.lang.ArithmeticException: Division( is)? undefined"))
            defaultTags()
          }
        }
      }
    }
  }

  def "test promise error response"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        get("promise-error") {
          Promise.async {
            0 / 0
          }.then {
            context.render(it)
          }
        }
      }
    }
    def request = new Request.Builder()
      .url(app.address.resolve("promise-error?query=param").toURL())
      .get()
      .build()
    when:
    def resp = client.newCall(request).execute()
    then:
    resp.code() == 500

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          resourceName "GET /promise-error"
          serviceName "unnamed-java-app"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          errored true
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "${app.address.resolve('promise-error')}"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            errorTags(ArithmeticException, Pattern.compile("Division( is)? undefined"))
            defaultTags()
          }
        }
        span(1) {
          resourceName "GET /promise-error"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(0))
          errored true
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "${app.address.resolve('promise-error')}"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.ERROR.key" true
            defaultTags()
          }
        }
      }
    }
  }

  def "test render error response"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        all {
          context.render(Promise.sync {
            return "fail " + 0 / 0
          })
        }
      }
    }
    def request = new Request.Builder()
      .url(app.address.resolve("?query=param").toURL())
      .get()
      .build()
    when:
    def resp = client.newCall(request).execute()
    then:
    resp.code() == 500

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          resourceName "GET /"
          serviceName "unnamed-java-app"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          errored true
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "$app.address"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            errorTags(ArithmeticException, Pattern.compile("Division( is)? undefined"))
            defaultTags()
          }
        }
        span(1) {
          resourceName "GET /"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(0))
          errored true
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "$app.address"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.ERROR.key" true
            defaultTags()
          }
        }
      }
    }
  }

  def "test path call using ratpack http client"() {
    setup:

    // Use jetty based server to avoid confusion.
    def external = httpServer {
      handlers {
        get("nested") {
          handleDistributedRequest()
          response.send("succ")
        }
        get("nested2") {
          handleDistributedRequest()
          response.send("ess")
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
      distributedRequestTrace(it, 0, trace(2).get(3))
      distributedRequestTrace(it, 1, trace(2).get(2))

      trace(2, 4) {
        // main app span that processed the request from OKHTTP request
        span(0) {
          resourceName "GET /"
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
            "$Tags.HTTP_URL.key" "$app.address"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            defaultTags()
          }
        }
        span(1) {
          resourceName "GET /"
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
            "$Tags.HTTP_URL.key" "$app.address"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_PORT.key" Integer
            defaultTags()
          }
        }
        // Second http client call that receives the 'ess' of Success
        span(2) {
          resourceName "GET /?"
          serviceName "unnamed-java-app"
          operationName "netty.client.request"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf(span(1))
          errored false
          tags {
            "$Tags.COMPONENT.key" "netty-client"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "${external.address}/nested2"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            defaultTags()
          }
        }
        // First http client call that receives the 'Succ' of Success
        span(3) {
          resourceName "GET /nested"
          serviceName "unnamed-java-app"
          operationName "netty.client.request"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf(span(1))
          errored false
          tags {
            "$Tags.COMPONENT.key" "netty-client"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "${external.address}/nested"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            defaultTags()
          }
        }
      }
    }
  }

  def "test ratpack http client error handling"() {
    setup:
    def badAddress = new URI("http://localhost:$UNUSABLE_PORT")

    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        get { HttpClient httpClient ->
          httpClient.get(badAddress)
            .map { it.body.text }
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
    resp.code() == 500

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          resourceName "GET /"
          serviceName "unnamed-java-app"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          errored true
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "$app.address"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.ERROR.key" true
            defaultTags()
          }
        }
        span(1) {
          resourceName "GET /"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(0))
          errored true
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "$app.address"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_PORT.key" Integer
            errorTags(ConnectException, String)
            defaultTags()
          }
        }
        span(2) {
          operationName "netty.connect"
          resourceName "netty.connect"
          childOf(span(1))
          errored true
          tags {
            "$Tags.COMPONENT.key" "netty"
            errorTags(ConnectException, String)
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
          TraceScope scope
          if (startSpanInHandler) {
            Span childSpan = GlobalTracer.get()
              .buildSpan("ratpack.exec-test")
              .withTag(DDTags.RESOURCE_NAME, "INSIDE-TEST")
              .start()
            scope = GlobalTracer.get().scopeManager().activate(childSpan, true)
          }
          def latch = new CountDownLatch(1)
          try {
            scope?.setAsyncPropagation(true)
            GlobalTracer.get().activeSpan().setBaggageItem("test-baggage", "foo")

            context.render(testPromise(latch).fork())
          } finally {
            scope?.close()
            latch.countDown()
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
    resp.body().string() == "foo"

    assertTraces(1) {
      trace(0, (startSpanInHandler ? 3 : 2)) {
        if (startSpanInHandler) {
          span(0) {
            resourceName "INSIDE-TEST"
            serviceName "unnamed-java-app"
            operationName "ratpack.exec-test"
            childOf(span(2))
            errored false
            tags {
              defaultTags()
            }
          }
        }
        span(startSpanInHandler ? 1 : 0) {
          resourceName "GET /"
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
            "$Tags.HTTP_URL.key" "$app.address"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            defaultTags()
          }
        }
        span(startSpanInHandler ? 2 : 1) {
          resourceName "GET /"
          serviceName "unnamed-java-app"
          operationName "ratpack.handler"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(startSpanInHandler ? 1 : 0))
          errored false
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "$app.address"
            "$Tags.PEER_HOSTNAME.key" "$app.address.host"
            "$Tags.PEER_PORT.key" Integer
            defaultTags()
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
  Promise<String> testPromise(CountDownLatch latch = null) {
    Promise.sync {
      latch?.await()
      Scope tracerScope = GlobalTracer.get().scopeManager().active()
      return tracerScope?.span()?.getBaggageItem("test-baggage")
    }
  }
}

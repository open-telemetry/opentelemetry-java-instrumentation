import datadog.opentracing.scopemanager.ContextualScopeManager
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.ratpack.impl.RatpackScopeManager
import io.opentracing.Scope
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
  OkHttpClient client = new OkHttpClient.Builder()
  // Uncomment when debugging:
//    .connectTimeout(1, TimeUnit.HOURS)
//    .writeTimeout(1, TimeUnit.HOURS)
//    .readTimeout(1, TimeUnit.HOURS)
    .build()


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

    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().serviceName == "unnamed-java-app"
    span.context().operationName == "ratpack.handler"
    span.context().resourceName == "GET /"
    span.context().tags["component"] == "handler"
    span.context().spanType == DDSpanTypes.WEB_SERVLET
    !span.context().getErrorFlag()
    span.context().tags["http.url"] == "/"
    span.context().tags["http.method"] == "GET"
    span.context().tags["span.kind"] == "server"
    span.context().tags["http.status_code"] == 200
    span.context().tags["thread.name"] != null
    span.context().tags["thread.id"] != null
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

    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().serviceName == "unnamed-java-app"
    span.context().operationName == "ratpack.handler"
    span.context().resourceName == "GET /:foo/:bar?/baz"
    span.context().tags["component"] == "handler"
    span.context().spanType == DDSpanTypes.WEB_SERVLET
    !span.context().getErrorFlag()
    span.context().tags["http.url"] == "/a/b/baz"
    span.context().tags["http.method"] == "GET"
    span.context().tags["span.kind"] == "server"
    span.context().tags["http.status_code"] == 200
    span.context().tags["thread.name"] != null
    span.context().tags["thread.id"] != null
  }

  def "test error response"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        get {
          context.clientError(500)
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

    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().getErrorFlag()
    span.context().serviceName == "unnamed-java-app"
    span.context().operationName == "ratpack.handler"
    span.context().resourceName == "GET /"
    span.context().tags["component"] == "handler"
    span.context().spanType == DDSpanTypes.WEB_SERVLET
    span.context().tags["http.url"] == "/"
    span.context().tags["http.method"] == "GET"
    span.context().tags["span.kind"] == "server"
    span.context().tags["http.status_code"] == 500
    span.context().tags["thread.name"] != null
    span.context().tags["thread.id"] != null
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
    TEST_WRITER.size() == 3
    def trace = TEST_WRITER.get(2)
    trace.size() == 3
    def span = trace[0]

    span.context().serviceName == "unnamed-java-app"
    span.context().operationName == "ratpack.handler"
    span.context().resourceName == "GET /"
    span.context().tags["component"] == "handler"
    span.context().spanType == DDSpanTypes.WEB_SERVLET
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
    clientTrace1.context().tags["component"] == "httpclient"
    !clientTrace1.context().getErrorFlag()
    clientTrace1.context().tags["http.url"] == "${external.address}nested2"
    clientTrace1.context().tags["http.method"] == "GET"
    clientTrace1.context().tags["span.kind"] == "client"
    clientTrace1.context().tags["http.status_code"] == 200
    clientTrace1.context().tags["thread.name"] != null
    clientTrace1.context().tags["thread.id"] != null

    def clientTrace2 = trace[2] // First http client call that receives the 'Succ' of Success

    clientTrace2.context().serviceName == "unnamed-java-app"
    clientTrace2.context().operationName == "ratpack.client-request"
    clientTrace2.context().tags["component"] == "httpclient"
    !clientTrace2.context().getErrorFlag()
    clientTrace2.context().tags["http.url"] == "${external.address}nested"
    clientTrace2.context().tags["http.method"] == "GET"
    clientTrace2.context().tags["span.kind"] == "client"
    clientTrace2.context().tags["http.status_code"] == 200
    clientTrace2.context().tags["thread.name"] != null
    clientTrace2.context().tags["thread.id"] != null

    def nestedTrace = TEST_WRITER.get(1)
    nestedTrace.size() == 1
    def nestedSpan = nestedTrace[0] // simulated external system, second call

    nestedSpan.context().serviceName == "unnamed-java-app"
    nestedSpan.context().operationName == "ratpack.handler"
    nestedSpan.context().resourceName == "GET /nested2"
    nestedSpan.context().tags["component"] == "handler"
    nestedSpan.context().spanType == DDSpanTypes.WEB_SERVLET
    !nestedSpan.context().getErrorFlag()
    nestedSpan.context().tags["http.url"] == "/nested2"
    nestedSpan.context().tags["http.method"] == "GET"
    nestedSpan.context().tags["span.kind"] == "server"
    nestedSpan.context().tags["http.status_code"] == 200
    nestedSpan.context().tags["thread.name"] != null
    nestedSpan.context().tags["thread.id"] != null

    def nestedTrace2 = TEST_WRITER.get(0)
    nestedTrace2.size() == 1
    def nestedSpan2 = nestedTrace2[0] // simulated external system, first call

    nestedSpan2.context().serviceName == "unnamed-java-app"
    nestedSpan2.context().operationName == "ratpack.handler"
    nestedSpan2.context().resourceName == "GET /nested"
    nestedSpan2.context().tags["component"] == "handler"
    nestedSpan2.context().spanType == DDSpanTypes.WEB_SERVLET
    !nestedSpan2.context().getErrorFlag()
    nestedSpan2.context().tags["http.url"] == "/nested"
    nestedSpan2.context().tags["http.method"] == "GET"
    nestedSpan2.context().tags["span.kind"] == "server"
    nestedSpan2.context().tags["http.status_code"] == 200
    nestedSpan2.context().tags["thread.name"] != null
    nestedSpan2.context().tags["thread.id"] != null
  }

  def "forked executions inherit parent scope"() {
    when:
    def result = ExecHarness.yieldSingle({ spec ->
      // This does the work of the initial instrumentation that occurs on the server registry. Because we are using
      // ExecHarness for testing this does not get executed by the instrumentation
      def ratpackScopeManager = new RatpackScopeManager()
      spec.add(ratpackScopeManager)
      ((ContextualScopeManager) GlobalTracer.get().scopeManager())
        .addScopeContext(ratpackScopeManager)
    }, {
      final Scope scope =
        GlobalTracer.get()
          .buildSpan("ratpack.exec-test")
          .startActive(true)
      scope.span().setBaggageItem("test-baggage", "foo")
      ParallelBatch.of(testPromise(), testPromise()).yield()
    })

    then:
    result.valueOrThrow == ["foo", "foo"]
  }

  Promise<String> testPromise() {
    Promise.sync {
      GlobalTracer.get().activeSpan().getBaggageItem("test-baggage")
    }
  }
}

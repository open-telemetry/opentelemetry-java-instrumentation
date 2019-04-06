import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.catalina.core.ApplicationFilterChain
import spock.lang.Shared
import spock.lang.Unroll

import javax.servlet.Servlet

// Need to be explicit to unroll inherited tests:
@Unroll
abstract class AbstractServlet3Test<CONTEXT> extends AgentTestRunner {

  @Shared
  OkHttpClient client = OkHttpUtils.clientBuilder().addNetworkInterceptor(new Interceptor() {
    @Override
    Response intercept(Interceptor.Chain chain) throws IOException {
      def response = chain.proceed(chain.request())
      TEST_WRITER.waitForTraces(1)
      return response
    }
  })
    .build()

  @Shared
  int port = PortUtils.randomOpenPort()
  @Shared
  protected String user = "user"
  @Shared
  protected String pass = "password"

  abstract String getContext()

  abstract void addServlet(CONTEXT context, String url, Class<Servlet> servlet)

  protected void setupServlets(CONTEXT context) {
    addServlet(context, "/sync", TestServlet3.Sync)
    addServlet(context, "/auth/sync", TestServlet3.Sync)
    addServlet(context, "/async", TestServlet3.Async)
    addServlet(context, "/auth/async", TestServlet3.Async)
    addServlet(context, "/blocking", TestServlet3.BlockingAsync)
    addServlet(context, "/dispatch/sync", TestServlet3.DispatchSync)
    addServlet(context, "/dispatch/async", TestServlet3.DispatchAsync)
    addServlet(context, "/dispatch/recursive", TestServlet3.DispatchRecursive)
    addServlet(context, "/recursive", TestServlet3.DispatchRecursive)
    addServlet(context, "/fake", TestServlet3.FakeAsync)
  }

  def "test #path servlet call (auth: #auth, distributed tracing: #distributedTracing)"() {
    setup:
    def requestBuilder = new Request.Builder()
      .url("http://localhost:$port/$context/$path")
      .get()
    if (distributedTracing) {
      requestBuilder.header("x-datadog-trace-id", "123")
      requestBuilder.header("x-datadog-parent-id", "456")
    }
    if (auth) {
      requestBuilder.header("Authorization", Credentials.basic(user, pass))
    }
    def response = client.newCall(requestBuilder.build()).execute()

    expect:
    response.body().string().trim() == expectedResponse

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          if (distributedTracing) {
            traceId "123"
            parentId "456"
          } else {
            parent()
          }
          serviceName context
          operationName "servlet.request"
          resourceName "GET /$context/$path"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "http.url" "http://localhost:$port/$context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.origin.type" { it == "TestServlet3\$$origin" || it == ApplicationFilterChain.name }
            "servlet.context" "/$context"
            "http.status_code" 200
            if (auth) {
              "$DDTags.USER_NAME" user
            }
            defaultTags(distributedTracing)
          }
        }
      }
    }

    where:
    path         | expectedResponse      | auth  | origin          | distributedTracing
    "async"      | "Hello Async"         | false | "Async"         | false
    "sync"       | "Hello Sync"          | false | "Sync"          | false
    "auth/async" | "Hello Async"         | true  | "Async"         | false
    "auth/sync"  | "Hello Sync"          | true  | "Sync"          | false
    "blocking"   | "Hello BlockingAsync" | false | "BlockingAsync" | false
    "fake"       | "Hello FakeAsync"     | false | "FakeAsync"     | false
    "async"      | "Hello Async"         | false | "Async"         | true
    "sync"       | "Hello Sync"          | false | "Sync"          | true
    "auth/async" | "Hello Async"         | true  | "Async"         | true
    "auth/sync"  | "Hello Sync"          | true  | "Sync"          | true
    "blocking"   | "Hello BlockingAsync" | false | "BlockingAsync" | true
    "fake"       | "Hello FakeAsync"     | false | "FakeAsync"     | true
  }

  def "test dispatch #path with depth #depth, distributed tracing: #distributedTracing"() {
    setup:
    def requestBuilder = new Request.Builder()
      .url("http://localhost:$port/$context/dispatch/$path?depth=$depth")
      .get()
    if (distributedTracing) {
      requestBuilder.header("x-datadog-trace-id", "123")
      requestBuilder.header("x-datadog-parent-id", "456")
    }
    def response = client.newCall(requestBuilder.build()).execute()

    expect:
    response.body().string().trim() == "Hello $origin"
    assertTraces(2 + depth) {
      for (int i = 0; i < depth; i++) {
        trace(i, 1) {
          span(0) {
            if (i == 0) {
              if (distributedTracing) {
                traceId "123"
                parentId "456"
              } else {
                parent()
              }
            } else {
              childOf TEST_WRITER[i - 1][0]
            }
            serviceName context
            operationName "servlet.request"
            resourceName "GET /$context/dispatch/$path"
            spanType DDSpanTypes.HTTP_SERVER
            errored false
            tags {
              "http.url" "http://localhost:$port/$context/dispatch/$path"
              "http.method" "GET"
              "span.kind" "server"
              "component" "java-web-servlet"
              "peer.hostname" "127.0.0.1"
              "peer.ipv4" "127.0.0.1"
              "peer.port" Integer
              "span.origin.type" { it == "TestServlet3\$Dispatch$origin" || it == ApplicationFilterChain.name }
              "http.status_code" 200
              "servlet.context" "/$context"
              "servlet.dispatch" "/dispatch/recursive?depth=${depth - i - 1}"
              defaultTags(i > 0 ? true : distributedTracing)
            }
          }
        }
      }
      // In case of recursive requests or sync request the most 'bottom' span is closed before its parent
      trace(depth, 1) {
        span(0) {
          serviceName context
          operationName "servlet.request"
          resourceName "GET /$context/$path"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          childOf TEST_WRITER[depth + 1][0]
          tags {
            "http.url" "http://localhost:$port/$context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.origin.type" {
              it == "TestServlet3\$$origin" || it == "TestServlet3\$DispatchRecursive" || it == ApplicationFilterChain.name
            }
            "http.status_code" 200
            "servlet.context" "/$context"
            defaultTags(true)
          }
        }
      }
      trace(depth + 1, 1) {
        span(0) {
          if (depth > 0) {
            childOf TEST_WRITER[depth - 1][0]
          } else {
            if (distributedTracing) {
              traceId "123"
              parentId "456"
            } else {
              parent()
            }
          }
          serviceName context
          operationName "servlet.request"
          resourceName "GET /$context/dispatch/$path"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "http.url" "http://localhost:$port/$context/dispatch/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.origin.type" { it == "TestServlet3\$Dispatch$origin" || it == ApplicationFilterChain.name }
            "http.status_code" 200
            "servlet.context" "/$context"
            "servlet.dispatch" "/$path"
            defaultTags(depth > 0 ? true : distributedTracing)
          }
        }
      }
    }

    where:
    path        | distributedTracing | depth
    "sync"      | true               | 0
    "sync"      | false              | 0
    "recursive" | true               | 0
    "recursive" | false              | 0
    "recursive" | true               | 1
    "recursive" | false              | 1
    "recursive" | true               | 20
    "recursive" | false              | 20

    origin = path.capitalize()
  }

  def "test dispatch async #path with depth #depth, distributed tracing: #distributedTracing"() {
    setup:
    def requestBuilder = new Request.Builder()
      .url("http://localhost:$port/$context/dispatch/$path")
      .get()
    if (distributedTracing) {
      requestBuilder.header("x-datadog-trace-id", "123")
      requestBuilder.header("x-datadog-parent-id", "456")
    }
    def response = client.newCall(requestBuilder.build()).execute()

    expect:
    response.body().string().trim() == "Hello $origin"
    assertTraces(2) {
      // Async requests have their parent span closed before child span
      trace(0, 1) {
        span(0) {
          if (distributedTracing) {
            traceId "123"
            parentId "456"
          } else {
            parent()
          }
          serviceName context
          operationName "servlet.request"
          resourceName "GET /$context/dispatch/$path"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "http.url" "http://localhost:$port/$context/dispatch/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.origin.type" { it == "TestServlet3\$Dispatch$origin" || it == ApplicationFilterChain.name }
            "http.status_code" 200
            "servlet.context" "/$context"
            "servlet.dispatch" "/$path"
            defaultTags(distributedTracing)
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName context
          operationName "servlet.request"
          resourceName "GET /$context/$path"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          childOf TEST_WRITER[0][0]
          tags {
            "http.url" "http://localhost:$port/$context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.origin.type" {
              it == "TestServlet3\$$origin" || it == "TestServlet3\$DispatchRecursive" || it == ApplicationFilterChain.name
            }
            "http.status_code" 200
            "servlet.context" "/$context"
            defaultTags(true)
          }
        }
      }
    }

    where:
    path    | distributedTracing
    "async" | true
    "async" | false

    origin = path.capitalize()
  }

  def "servlet instrumentation clears state after async request"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/$context/$path")
      .get()
      .build()
    def numTraces = 1
    for (int i = 0; i < numTraces; ++i) {
      client.newCall(request).execute()
    }

    expect:
    assertTraces(dispatched ? numTraces * 2 : numTraces) {
      for (int i = 0; (dispatched ? i + 1 : i) < TEST_WRITER.size(); i += (dispatched ? 2 : 1)) {
        if (dispatched) {
          trace(i, 1) {
            span(0) {
              operationName "servlet.request"
              resourceName "GET /$context/dispatch/async"
              spanType DDSpanTypes.HTTP_SERVER
              parent()
            }
          }
        }
        trace(dispatched ? i + 1 : i, 1) {
          span(0) {
            operationName "servlet.request"
            resourceName "GET /$context/async"
            spanType DDSpanTypes.HTTP_SERVER
            if (dispatched) {
              childOf TEST_WRITER[i][0]
            } else {
              parent()
            }
          }
        }
      }
    }

    where:
    path             | dispatched
    "async"          | false
    "dispatch/async" | true
  }

  def "test #path error servlet call"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/$context/$path?error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName context
          operationName "servlet.request"
          resourceName "GET /$context/$path"
          spanType DDSpanTypes.HTTP_SERVER
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$port/$context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.origin.type" { it == "TestServlet3\$$origin" || it == ApplicationFilterChain.name }
            "servlet.context" "/$context"
            "http.status_code" 500
            errorTags(RuntimeException, "some $path error")
            defaultTags()
          }
        }
      }
    }

    where:
    path   | expectedResponse
    //"async" | "Hello Async" // FIXME: I can't seem get the async error handler to trigger
    "sync" | "Hello Sync"

    origin = path.capitalize()
  }

  def "test #path non-throwing-error servlet call"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/$context/$path?non-throwing-error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName context
          operationName "servlet.request"
          resourceName "GET /$context/$path"
          spanType DDSpanTypes.HTTP_SERVER
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$port/$context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.origin.type" { it == "TestServlet3\$$origin" || it == ApplicationFilterChain.name }
            "servlet.context" "/$context"
            "http.status_code" 500
            "error" true
            defaultTags()
          }
        }
      }
    }

    where:
    path   | expectedResponse
    "sync" | "Hello Sync"

    origin = path.capitalize()
  }
}

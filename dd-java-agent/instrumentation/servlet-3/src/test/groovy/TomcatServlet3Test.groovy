import com.google.common.io.Files
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.api.DDSpanTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.catalina.Context
import org.apache.catalina.core.ApplicationFilterChain
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.JarScanFilter
import org.apache.tomcat.JarScanType
import spock.lang.Shared

class TomcatServlet3Test extends AgentTestRunner {

  OkHttpClient client = OkHttpUtils.client()

  @Shared
  int port
  @Shared
  Tomcat tomcatServer
  @Shared
  Context appContext

  def setupSpec() {
    port = TestUtils.randomOpenPort()
    tomcatServer = new Tomcat()
    tomcatServer.setPort(port)
    tomcatServer.getConnector()

    def baseDir = Files.createTempDir()
    baseDir.deleteOnExit()
    tomcatServer.setBaseDir(baseDir.getAbsolutePath())

    final File applicationDir = new File(baseDir, "/webapps/ROOT")
    if (!applicationDir.exists()) {
      applicationDir.mkdirs()
    }
    appContext = tomcatServer.addWebapp("/my-context", applicationDir.getAbsolutePath())
    // Speed up startup by disabling jar scanning:
    appContext.getJarScanner().setJarScanFilter(new JarScanFilter() {
      @Override
      boolean check(JarScanType jarScanType, String jarName) {
        return false
      }
    })

    Tomcat.addServlet(appContext, "syncServlet", new TestServlet3.Sync())
    appContext.addServletMappingDecoded("/sync", "syncServlet")

    Tomcat.addServlet(appContext, "asyncServlet", new TestServlet3.Async())
    appContext.addServletMappingDecoded("/async", "asyncServlet")

    Tomcat.addServlet(appContext, "blockingServlet", new TestServlet3.BlockingAsync())
    appContext.addServletMappingDecoded("/blocking", "blockingServlet")

    Tomcat.addServlet(appContext, "dispatchServlet", new TestServlet3.DispatchSync())
    appContext.addServletMappingDecoded("/dispatch/sync", "dispatchServlet")

    Tomcat.addServlet(appContext, "dispatchAsyncServlet", new TestServlet3.DispatchAsync())
    appContext.addServletMappingDecoded("/dispatch/async", "dispatchAsyncServlet")

    Tomcat.addServlet(appContext, "fakeServlet", new TestServlet3.FakeAsync())
    appContext.addServletMappingDecoded("/fake", "fakeServlet")

    tomcatServer.start()
    System.out.println(
      "Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + port + "/")
  }

  def cleanupSpec() {
    tomcatServer.stop()
    tomcatServer.destroy()
  }

  def "test #path servlet call (auth: #auth, distributed tracing: #distributedTracing)"() {
    setup:
    def requestBuilder = new Request.Builder()
      .url("http://localhost:$port/my-context/$path")
      .get()
    if (distributedTracing) {
      requestBuilder.header("x-datadog-trace-id", "123")
      requestBuilder.header("x-datadog-parent-id", "456")
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
          serviceName "my-context"
          operationName "servlet.request"
          resourceName "GET /my-context/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$port/my-context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "span.origin.type" ApplicationFilterChain.name
            "servlet.context" "/my-context"
            "http.status_code" 200
            defaultTags(distributedTracing)
          }
        }
      }
    }

    where:
    path       | expectedResponse      | origin          | distributedTracing
    "async"    | "Hello Async"         | "Async"         | false
    "sync"     | "Hello Sync"          | "Sync"          | false
    "blocking" | "Hello BlockingAsync" | "BlockingAsync" | false
    "fake"     | "Hello FakeAsync"     | "FakeAsync"     | false
    "async"    | "Hello Async"         | "Async"         | true
    "sync"     | "Hello Sync"          | "Sync"          | true
    "blocking" | "Hello BlockingAsync" | "BlockingAsync" | true
    "fake"     | "Hello FakeAsync"     | "FakeAsync"     | true
  }

  def "test dispatch #path"() {
    setup:
    def requestBuilder = new Request.Builder()
      .url("http://localhost:$port/my-context/dispatch/$path")
      .get()
    if (distributedTracing) {
      requestBuilder.header("x-datadog-trace-id", "123")
      requestBuilder.header("x-datadog-parent-id", "456")
    }
    def response = client.newCall(requestBuilder.build()).execute()

    expect:
    response.body().string().trim() == "Hello $type"

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          if (distributedTracing) {
            traceId "123"
            parentId "456"
          } else {
            parent()
          }
          serviceName "my-context"
          operationName "servlet.request"
          resourceName "GET /my-context/dispatch/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$port/my-context/dispatch/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "span.type" DDSpanTypes.WEB_SERVLET
            "http.status_code" 200
            "servlet.context" "/my-context"
            "servlet.dispatch" "/$path"
            defaultTags(distributedTracing)
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "my-context"
          operationName "servlet.request"
          resourceName "GET /my-context/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          childOf TEST_WRITER[0][0]
          tags {
            "http.url" "http://localhost:$port/my-context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "span.type" DDSpanTypes.WEB_SERVLET
            "http.status_code" 200
            "servlet.context" "/my-context"
            defaultTags(true)
          }
        }
      }
    }

    where:
    path    | distributedTracing
    "sync"  | true
    "sync"  | false
    "async" | true
    "async" | false

    type = path.capitalize()
  }

  def "servlet instrumentation clears state after async request"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/my-context/$path")
      .get()
      .build()
    def numTraces = 10
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
              resourceName "GET /my-context/dispatch/async"
              parent()
            }
          }
        }
        trace(dispatched ? i + 1 : i, 1) {
          span(0) {
            operationName "servlet.request"
            resourceName "GET /my-context/async"
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
      .url("http://localhost:$port/my-context/$path?error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "my-context"
          operationName "servlet.request"
          resourceName "GET /my-context/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$port/my-context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "span.origin.type" ApplicationFilterChain.name
            "servlet.context" "/my-context"
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
  }

  def "test #path non-throwing-error servlet call"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/my-context/$path?non-throwing-error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "my-context"
          operationName "servlet.request"
          resourceName "GET /my-context/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$port/my-context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "span.origin.type" ApplicationFilterChain.name
            "servlet.context" "/my-context"
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
  }
}

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

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces

class TomcatServlet3Test extends AgentTestRunner {

  OkHttpClient client = OkHttpUtils.client()

  int port
  Tomcat tomcatServer
  Context appContext

  def setup() {
    port = TestUtils.randomOpenPort()
    tomcatServer = new Tomcat()
    tomcatServer.setPort(port)

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

    tomcatServer.start()
    System.out.println(
      "Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + port + "/")
  }

  def cleanup() {
    tomcatServer.stop()
    tomcatServer.destroy()
  }

  def "test #path servlet call"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/my-context/$path")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() == expectedResponse

    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "my-context"
          operationName "servlet.request"
          resourceName "GET /my-context/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          parent()
          tags {
            "http.url" "http://localhost:$port/my-context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "span.origin.type" ApplicationFilterChain.name
            "servlet.context" "/my-context"
            "http.status_code" 200
            defaultTags()
          }
        }
      }
    }

    where:
    path    | expectedResponse
    "async" | "Hello Async"
    "sync"  | "Hello Sync"
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

    assertTraces(TEST_WRITER, 1) {
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

  def "test #path error servlet call for non-throwing error"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/my-context/$path?non-throwing-error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(TEST_WRITER, 1) {
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

import com.google.common.io.Files
import datadog.opentracing.DDTracer
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.common.writer.ListWriter
import io.opentracing.util.GlobalTracer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.JarScanFilter
import org.apache.tomcat.JarScanType

import java.lang.reflect.Field

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class TomcatServlet3Test extends AgentTestRunner {

  static final int PORT = TestUtils.randomOpenPort()
  OkHttpClient client = new OkHttpClient.Builder()
  // Uncomment when debugging:
//    .connectTimeout(1, TimeUnit.HOURS)
//    .writeTimeout(1, TimeUnit.HOURS)
//    .readTimeout(1, TimeUnit.HOURS)
    .build()

  Tomcat tomcatServer
  Context appContext

  ListWriter writer = new ListWriter()
  DDTracer tracer = new DDTracer(writer)

  def setup() {
    tomcatServer = new Tomcat()
    tomcatServer.setPort(PORT)

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
      "Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + PORT + "/")


    try {
      GlobalTracer.register(tracer)
    } catch (final Exception e) {
      // Force it anyway using reflection
      final Field field = GlobalTracer.getDeclaredField("tracer")
      field.setAccessible(true)
      field.set(null, tracer)
    }
    writer.start()
    assert GlobalTracer.isRegistered()
  }

  def cleanup() {
    tomcatServer.stop()
    tomcatServer.destroy()
  }

  def "test #path servlet call"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$PORT/my-context/$path")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() == expectedResponse

    assertTraces(writer, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "my-context"
          operationName "servlet.request"
          resourceName "GET /my-context/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          parent()
          tags {
            "http.url" "http://localhost:$PORT/my-context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
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
      .url("http://localhost:$PORT/my-context/$path?error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(writer, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "my-context"
          operationName "servlet.request"
          resourceName "GET /my-context/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$PORT/my-context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
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
      .url("http://localhost:$PORT/my-context/$path?non-throwing-error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(writer, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "my-context"
          operationName "servlet.request"
          resourceName "GET /my-context/$path"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$PORT/my-context/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
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

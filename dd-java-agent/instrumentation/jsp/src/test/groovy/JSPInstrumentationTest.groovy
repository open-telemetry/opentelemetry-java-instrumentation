import com.google.common.io.Files
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.api.DDSpanTypes
import io.netty.handler.codec.http.HttpResponseStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.JarScanFilter
import org.apache.tomcat.JarScanType
import spock.lang.Shared

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class JSPInstrumentationTest extends AgentTestRunner {

  static final int PORT = TestUtils.randomOpenPort()
  OkHttpClient client = new OkHttpClient.Builder()
  // Uncomment when debugging:
//    .connectTimeout(1, TimeUnit.HOURS)
//    .writeTimeout(1, TimeUnit.HOURS)
//    .readTimeout(1, TimeUnit.HOURS)
    .build()

  static Tomcat tomcatServer
  static Context appContext
  static final String JSP_WEBAPP_CONTEXT = "/jsptest-context"

  @Shared
  static File baseDir
  static String expectedJspClassFilesDir = "/work/Tomcat/localhost$JSP_WEBAPP_CONTEXT/org/apache/jsp/"

  def setupSpec() {
    tomcatServer = new Tomcat()
    tomcatServer.setPort(PORT)

    baseDir = Files.createTempDir()
    baseDir.deleteOnExit()
    expectedJspClassFilesDir = baseDir.getCanonicalFile().getAbsolutePath() + expectedJspClassFilesDir
    tomcatServer.setBaseDir(baseDir.getAbsolutePath())

    appContext = tomcatServer.addWebapp(JSP_WEBAPP_CONTEXT,
      JSPInstrumentationTest.getResource("/webapps/jsptest").getPath())
    // Speed up startup by disabling jar scanning:
    appContext.getJarScanner().setJarScanFilter(new JarScanFilter() {
      @Override
      boolean check(JarScanType jarScanType, String jarName) {
        return false
      }
    })

    tomcatServer.start()
    System.out.println(
      "Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + PORT + "/")
  }

  def cleanupSpec() {
    tomcatServer.stop()
    tomcatServer.destroy()
  }

  def "basic looping jsp"() {
    setup:
    String url = "http://localhost:$PORT$JSP_WEBAPP_CONTEXT/loop.jsp"
    def req = new Request.Builder().url(new URL(url)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "jsptest-context"
          operationName "jsp.service"
          resourceName "GET /jsptest-context/loop.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" url
            "http.method" "GET"
            "span.kind" "server"
            "component" "tomcat-jsp-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" JSP_WEBAPP_CONTEXT
            "http.status_code" 200
            "jsp.classFQCN" "org.apache.jsp.loop_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            "jsp.outputDir" expectedJspClassFilesDir
            "jsp.precompile" false
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()
  }

}

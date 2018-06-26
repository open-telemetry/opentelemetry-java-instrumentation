import com.google.common.io.Files
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.api.DDSpanTypes
import io.netty.handler.codec.http.HttpResponseStatus
import okhttp3.*
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.jasper.JasperException
import spock.lang.Unroll

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class JSPInstrumentationForwardTests extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.jsp.enabled", "true")
    // skip jar scanning using environment variables:
    // http://tomcat.apache.org/tomcat-7.0-doc/config/systemprops.html#JAR_Scanning
    // having this set allows us to test with old versions of the tomcat api since
    // JarScanFilter did not exist in the tomcat 7 api
    System.setProperty("org.apache.catalina.startup.ContextConfig.jarsToSkip", "*")
    System.setProperty("org.apache.catalina.startup.TldConfig.jarsToSkip", "*")
  }

  static final int PORT = TestUtils.randomOpenPort()
  OkHttpClient client = new OkHttpClient.Builder()
  // Uncomment when debugging:
//    .connectTimeout(1, TimeUnit.HOURS)
//    .writeTimeout(1, TimeUnit.HOURS)
//    .readTimeout(1, TimeUnit.HOURS)
    .build()

  static Tomcat tomcatServer
  static Context appContext
  static final String JSP_WEBAPP_CONTEXT = "jsptest-context"

  static File baseDir
  static String baseUrl
  static String expectedJspClassFilesDir = "/work/Tomcat/localhost/$JSP_WEBAPP_CONTEXT/org/apache/jsp/"

  def setupSpec() {
    tomcatServer = new Tomcat()
    tomcatServer.setPort(PORT)
    // comment to debug
    tomcatServer.setSilent(true)

    baseDir = Files.createTempDir()
    baseDir.deleteOnExit()
    expectedJspClassFilesDir = baseDir.getCanonicalFile().getAbsolutePath() + expectedJspClassFilesDir
    baseUrl = "http://localhost:$PORT/$JSP_WEBAPP_CONTEXT"
    tomcatServer.setBaseDir(baseDir.getAbsolutePath())

    appContext = tomcatServer.addWebapp("/$JSP_WEBAPP_CONTEXT",
      JSPInstrumentationForwardTests.getResource("/webapps/jsptest").getPath())

    tomcatServer.start()
    System.out.println(
      "Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + PORT + "/")
  }

  def cleanupSpec() {
    tomcatServer.stop()
    tomcatServer.destroy()
  }

  @Unroll
  def "non-erroneous GET forward to #forwardTo"() {
    setup:
    String reqUrl = baseUrl + "/$forwardFromFileName"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 5) {
        span(0) {
          parent()
          serviceName JSP_WEBAPP_CONTEXT
          operationName "servlet.request"
          resourceName "GET /$JSP_WEBAPP_CONTEXT/$forwardFromFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$PORT/$JSP_WEBAPP_CONTEXT/$forwardFromFileName"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/$forwardFromFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(1)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/$forwardDestFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/$forwardFromFileName"
            "jsp.requestURL" baseUrl + "/$forwardDestFileName"
            defaultTags()
          }
        }
        span(3) {
          childOf span(1)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/$forwardDestFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.$jspForwardDestClassPrefix$jspForwardDestClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(4) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/$forwardFromFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.$jspForwardFromClassPrefix$jspForwardFromClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()

    cleanup:
    res.close()

    where:
    forwardTo | forwardFromFileName | forwardDestFileName | jspForwardFromClassName | jspForwardFromClassPrefix | jspForwardDestClassName | jspForwardDestClassPrefix
    "no java jsp" | "forwards/forwardToNoJavaJsp.jsp" | "nojava.jsp" | "forwardToNoJavaJsp_jsp" | "forwards." | "nojava_jsp" | ""
    "normal java jsp" | "forwards/forwardToSimpleJava.jsp" | "common/loop.jsp" | "forwardToSimpleJava_jsp" | "forwards." | "loop_jsp" | "common."
  }

  def "non-erroneous GET forward to plain HTML"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToHtml.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName JSP_WEBAPP_CONTEXT
          operationName "servlet.request"
          resourceName "GET /$JSP_WEBAPP_CONTEXT/forwards/forwardToHtml.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$PORT/$JSP_WEBAPP_CONTEXT/forwards/forwardToHtml.jsp"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/forwards/forwardToHtml.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/forwards/forwardToHtml.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToHtml_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()

    cleanup:
    res.close()
  }

  def "non-erroneous GET forwarded to jsp with multiple includes"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToIncludeMulti.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 9) {
        span(0) {
          parent()
          serviceName JSP_WEBAPP_CONTEXT
          operationName "servlet.request"
          resourceName "GET /$JSP_WEBAPP_CONTEXT/forwards/forwardToIncludeMulti.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$PORT/$JSP_WEBAPP_CONTEXT/forwards/forwardToIncludeMulti.jsp"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/forwards/forwardToIncludeMulti.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(1)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/includes/includeMulti.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" baseUrl + "/includes/includeMulti.jsp"
            defaultTags()
          }
        }
        span(3) {
          childOf span(2)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" baseUrl + "/includes/includeMulti.jsp"
            defaultTags()
          }
        }
        span(4) {
          childOf span(2)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(5) {
          childOf span(2)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" baseUrl + "/includes/includeMulti.jsp"
            defaultTags()
          }
        }
        span(6) {
          childOf span(2)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(7) {
          childOf span(1)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/includes/includeMulti.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.includes.includeMulti_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(8) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/forwards/forwardToIncludeMulti.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToIncludeMulti_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()

    cleanup:
    res.close()
  }

  def "non-erroneous GET forward to another forward (2 forwards)"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToJspForward.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 7) {
        span(0) {
          parent()
          serviceName JSP_WEBAPP_CONTEXT
          operationName "servlet.request"
          resourceName "GET /$JSP_WEBAPP_CONTEXT/forwards/forwardToJspForward.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$PORT/$JSP_WEBAPP_CONTEXT/forwards/forwardToJspForward.jsp"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/forwards/forwardToJspForward.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(1)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/forwards/forwardToSimpleJava.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/forwards/forwardToJspForward.jsp"
            "jsp.requestURL" baseUrl + "/forwards/forwardToSimpleJava.jsp"
            defaultTags()
          }
        }
        span(3) {
          childOf span(2)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/common/loop.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/forwards/forwardToJspForward.jsp"
            "jsp.requestURL" baseUrl + "/common/loop.jsp"
            defaultTags()
          }
        }
        span(4) {
          childOf span(2)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/common/loop.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.common.loop_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(5) {
          childOf span(1)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/forwards/forwardToSimpleJava.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToSimpleJava_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(6) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/forwards/forwardToJspForward.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToJspForward_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()

    cleanup:
    res.close()
  }

  def "forward to jsp with compile error should not produce a 2nd render span"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToCompileError.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 4) {
        span(0) {
          parent()
          serviceName JSP_WEBAPP_CONTEXT
          operationName "servlet.request"
          resourceName "GET /$JSP_WEBAPP_CONTEXT/forwards/forwardToCompileError.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          tags {
            "http.url" "http://localhost:$PORT/$JSP_WEBAPP_CONTEXT/forwards/forwardToCompileError.jsp"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "http.status_code" 500
            errorTags(JasperException, String)
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/forwards/forwardToCompileError.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            errorTags(JasperException, String)
            defaultTags()
          }
        }
        span(2) {
          childOf span(1)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/compileError.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.compileError_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            "jsp.javaFile" expectedJspClassFilesDir + "compileError_jsp.java"
            "jsp.classpath" String
            errorTags(JasperException, String)
            defaultTags()
          }
        }
        span(3) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/forwards/forwardToCompileError.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToCompileError_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.INTERNAL_SERVER_ERROR.code()

    cleanup:
    res.close()
  }

  def "forward to non existent jsp should be 404"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToNonExistent.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName JSP_WEBAPP_CONTEXT
          operationName "servlet.request"
          resourceName "404"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$PORT/$JSP_WEBAPP_CONTEXT/forwards/forwardToNonExistent.jsp"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "http.status_code" 404
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/forwards/forwardToNonExistent.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.compile"
          resourceName "/forwards/forwardToNonExistent.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToNonExistent_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.NOT_FOUND.code()

    cleanup:
    res.close()
  }
}

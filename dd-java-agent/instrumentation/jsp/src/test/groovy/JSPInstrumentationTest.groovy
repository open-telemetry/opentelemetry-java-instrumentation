import com.google.common.io.Files
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.api.DDSpanTypes
import io.netty.handler.codec.http.HttpResponseStatus
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.JarScanFilter
import org.apache.tomcat.JarScanType
import spock.lang.Shared
import spock.lang.Unroll

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class JSPInstrumentationTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.jsp.enabled", "true")
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

  @Shared
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

  @Unroll
  def "non-erroneous GET #test test"() {
    setup:
    String reqUrl = baseUrl + "/$jspFileName"
    def req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/$jspFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            "http.status_code" 200
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()

    where:
    test | jspFileName
    "no java jsp" | "nojava.jsp"
    "basic loop jsp"|"common/loop.jsp"
    "invalid HTML markup"|"invalidMarkup.jsp"
  }

  def "non-erroneous GET with query string"() {
    setup:
    String queryString = "HELLO"
    String reqUrl = baseUrl + "/getQuery.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl + "?" + queryString)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/getQuery.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            "http.status_code" 200
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()
  }

  def "non-erroneous POST"() {
    setup:
    String reqUrl = baseUrl + "/post.jsp"
    RequestBody requestBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("name", "world")
      .build()
    Request req = new Request.Builder().url(new URL(reqUrl)).post(requestBody).build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/post.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "POST"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            "http.status_code" 200
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()
  }

  @Unroll
  def "erroneous runtime errors GET jsp with #test test"() {
    setup:
    String reqUrl = baseUrl + "/$jspFileName"
    def req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/$jspFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            "http.status_code" 500
            errorTags(exceptionClass, errorMessage)
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.INTERNAL_SERVER_ERROR.code()

    where:
    test | jspFileName | exceptionClass | errorMessage
    "java runtime error" | "runtimeError.jsp" | ArithmeticException | String
    "invalid write" | "invalidWrite.jsp" | StringIndexOutOfBoundsException | String
    "missing query gives null" | "getQuery.jsp" | NullPointerException | null
  }

  def "non-erroneous multi GET"() {
    setup:
    String reqUrl = baseUrl + "/includes/includeMulti.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    println(res.body().string())
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/includes/includeMulti.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            "http.status_code" 200
            defaultTags()
          }
        }
        span(2) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            "http.status_code" 200
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()
  }

  @Unroll
  def "non-erroneous GET forward to #forwardTo"() {
    setup:
    String reqUrl = baseUrl + "/$forwardFromFileName"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    println(res.body().string())
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/$forwardFromFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/$forwardDestFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/$forwardFromFileName"
            "jsp.requestURL" baseUrl + "/$forwardDestFileName"
            "http.status_code" 200
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()

    where:
    forwardTo | forwardFromFileName | forwardDestFileName
    "no java jsp" | "forwards/forwardToNoJavaJsp.jsp" | "nojava.jsp"
    "normal java jsp" | "forwards/forwardToSimpleJava.jsp" | "common/loop.jsp"
  }

  def "non-erroneous GET forward to plain HTML"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToHtml.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    println(res.body().string())
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/forwards/forwardToHtml.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            "http.status_code" 200
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()
  }

  def "non-erroneous GET forwarded to jsp with multiple includes"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToIncludeMulti.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    println(res.body().string())
    assertTraces(TEST_WRITER, 1) {
      trace(0, 4) {
        span(0) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/forwards/forwardToIncludeMulti.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/includes/includeMulti.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" baseUrl + "/includes/includeMulti.jsp"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(2) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" baseUrl + "/includes/includeMulti.jsp"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(3) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" baseUrl + "/includes/includeMulti.jsp"
            "http.status_code" 200
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()
  }

  def "non-erroneous GET forward to another forward (2 forwards)"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToJspForward.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    println(res.body().string())
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/forwards/forwardToJspForward.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.requestURL" reqUrl
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/forwards/forwardToSimpleJava.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/forwards/forwardToJspForward.jsp"
            "jsp.requestURL" baseUrl + "/forwards/forwardToSimpleJava.jsp"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(2) {
          serviceName JSP_WEBAPP_CONTEXT
          operationName "jsp.render"
          resourceName "/common/loop.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.method" "GET"
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$JSP_WEBAPP_CONTEXT"
            "jsp.forwardOrigin" "/forwards/forwardToJspForward.jsp"
            "jsp.requestURL" baseUrl + "/common/loop.jsp"
            "http.status_code" 200
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()
  }
}

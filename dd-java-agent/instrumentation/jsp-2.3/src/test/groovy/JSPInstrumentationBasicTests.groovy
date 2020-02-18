import com.google.common.io.Files
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.jasper.JasperException
import org.eclipse.jetty.http.HttpStatus
import spock.lang.Shared
import spock.lang.Unroll

class JSPInstrumentationBasicTests extends AgentTestRunner {

  static {
    // skip jar scanning using environment variables:
    // http://tomcat.apache.org/tomcat-7.0-doc/config/systemprops.html#JAR_Scanning
    // having this set allows us to test with old versions of the tomcat api since
    // JarScanFilter did not exist in the tomcat 7 api
    System.setProperty("org.apache.catalina.startup.ContextConfig.jarsToSkip", "*")
    System.setProperty("org.apache.catalina.startup.TldConfig.jarsToSkip", "*")
  }

  @Shared
  int port
  @Shared
  Tomcat tomcatServer
  @Shared
  Context appContext
  @Shared
  String jspWebappContext = "jsptest-context"

  @Shared
  File baseDir
  @Shared
  String baseUrl

  OkHttpClient client = OkHttpUtils.client()

  def setupSpec() {
    baseDir = Files.createTempDir()
    baseDir.deleteOnExit()

    port = PortUtils.randomOpenPort()

    tomcatServer = new Tomcat()
    tomcatServer.setBaseDir(baseDir.getAbsolutePath())
    tomcatServer.setPort(port)
    tomcatServer.getConnector()
    // comment to debug
    tomcatServer.setSilent(true)
    // this is needed in tomcat 9, this triggers the creation of a connector, will not
    // affect tomcat 7 and 8
    // https://stackoverflow.com/questions/48998387/code-works-with-embedded-apache-tomcat-8-but-not-with-9-whats-changed
    tomcatServer.getConnector()
    baseUrl = "http://localhost:$port/$jspWebappContext"

    appContext = tomcatServer.addWebapp("/$jspWebappContext",
      JSPInstrumentationBasicTests.getResource("/webapps/jsptest").getPath())

    tomcatServer.start()
    System.out.println(
      "Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + port + "/")
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
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/$jspFileName"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "$Tags.COMPONENT" "java-web-servlet"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "http://localhost:$port/$jspWebappContext/$jspFileName"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/$jspFileName"
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/$jspFileName"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "span.origin.type" jspClassName
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/$jspFileName"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.$jspClassNamePrefix$jspClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpStatus.OK_200

    cleanup:
    res.close()

    where:
    test                  | jspFileName         | jspClassName        | jspClassNamePrefix
    "no java jsp"         | "nojava.jsp"        | "nojava_jsp"        | ""
    "basic loop jsp"      | "common/loop.jsp"   | "loop_jsp"          | "common."
    "invalid HTML markup" | "invalidMarkup.jsp" | "invalidMarkup_jsp" | ""
  }

  def "non-erroneous GET with query string"() {
    setup:
    String queryString = "HELLO"
    String reqUrl = baseUrl + "/getQuery.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl + "?" + queryString)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/getQuery.jsp"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "$Tags.COMPONENT" "java-web-servlet"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "http://localhost:$port/$jspWebappContext/getQuery.jsp"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/getQuery.jsp"
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/getQuery.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "span.origin.type" "getQuery_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/getQuery.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.getQuery_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpStatus.OK_200

    cleanup:
    res.close()
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
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "POST /$jspWebappContext/post.jsp"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "$Tags.COMPONENT" "java-web-servlet"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "http://localhost:$port/$jspWebappContext/post.jsp"
            "$Tags.HTTP_METHOD" "POST"
            "$Tags.HTTP_STATUS" 200
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/post.jsp"
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/post.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "span.origin.type" "post_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/post.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.post_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpStatus.OK_200

    cleanup:
    res.close()
  }

  @Unroll
  def "erroneous runtime errors GET jsp with #test test"() {
    setup:
    String reqUrl = baseUrl + "/$jspFileName"
    def req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/$jspFileName"
          spanType DDSpanTypes.HTTP_SERVER
          errored true
          tags {
            "$Tags.COMPONENT" "java-web-servlet"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "http://localhost:$port/$jspWebappContext/$jspFileName"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 500
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/$jspFileName"
            "error.type" { String tagExceptionType ->
              return tagExceptionType == exceptionClass.getName() || tagExceptionType.contains(exceptionClass.getSimpleName())
            }
            "error.msg" { String tagErrorMsg ->
              return errorMessageOptional || tagErrorMsg instanceof String
            }
            "error.stack" String
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/$jspFileName"
          errored true
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "span.origin.type" jspClassName
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            "error.type" { String tagExceptionType ->
              return tagExceptionType == exceptionClass.getName() || tagExceptionType.contains(exceptionClass.getSimpleName())
            }
            "error.msg" { String tagErrorMsg ->
              return errorMessageOptional || tagErrorMsg instanceof String
            }
            "error.stack" String
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/$jspFileName"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.$jspClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpStatus.INTERNAL_SERVER_ERROR_500

    cleanup:
    res.close()

    where:
    test                       | jspFileName        | jspClassName       | exceptionClass            | errorMessageOptional
    "java runtime error"       | "runtimeError.jsp" | "runtimeError_jsp" | ArithmeticException       | false
    "invalid write"            | "invalidWrite.jsp" | "invalidWrite_jsp" | IndexOutOfBoundsException | true
    "missing query gives null" | "getQuery.jsp"     | "getQuery_jsp"     | NullPointerException      | true
  }

  def "non-erroneous include plain HTML GET"() {
    setup:
    String reqUrl = baseUrl + "/includes/includeHtml.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/includes/includeHtml.jsp"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "$Tags.COMPONENT" "java-web-servlet"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "http://localhost:$port/$jspWebappContext/includes/includeHtml.jsp"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/includes/includeHtml.jsp"
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/includes/includeHtml.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "span.origin.type" "includeHtml_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/includes/includeHtml.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.includes.includeHtml_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpStatus.OK_200

    cleanup:
    res.close()
  }

  def "non-erroneous multi GET"() {
    setup:
    String reqUrl = baseUrl + "/includes/includeMulti.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(1) {
      trace(0, 7) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/includes/includeMulti.jsp"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "$Tags.COMPONENT" "java-web-servlet"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "http://localhost:$port/$jspWebappContext/includes/includeMulti.jsp"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/includes/includeMulti.jsp"
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/includes/includeMulti.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "span.origin.type" "includeMulti_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "span.origin.type" "javaLoopH2_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(3) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/common/javaLoopH2.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(4) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "span.origin.type" "javaLoopH2_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(5) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/common/javaLoopH2.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(6) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/includes/includeMulti.jsp"
          errored false
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.includes.includeMulti_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpStatus.OK_200

    cleanup:
    res.close()
  }

  def "#test compile error should not produce render traces and spans"() {
    setup:
    String reqUrl = baseUrl + "/$jspFileName"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/$jspFileName"
          spanType DDSpanTypes.HTTP_SERVER
          errored true
          tags {
            "$Tags.COMPONENT" "java-web-servlet"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "http://localhost:$port/$jspWebappContext/$jspFileName"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 500
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/$jspFileName"
            errorTags(JasperException, String)
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/$jspFileName"
          errored true
          tags {
            "$Tags.COMPONENT" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.$jspClassNamePrefix$jspClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            errorTags(JasperException, String)
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpStatus.INTERNAL_SERVER_ERROR_500

    cleanup:
    res.close()

    where:
    test      | jspFileName                            | jspClassName                  | jspClassNamePrefix
    "normal"  | "compileError.jsp"                     | "compileError_jsp"            | ""
    "forward" | "forwards/forwardWithCompileError.jsp" | "forwardWithCompileError_jsp" | "forwards."
  }

  def "direct static file reference"() {
    setup:
    String reqUrl = baseUrl + "/$staticFile"
    def req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    res.code() == HttpStatus.OK_200
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          // FIXME: this is not a great resource name for serving static content.
          resourceName "GET /$jspWebappContext/$staticFile"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "$Tags.COMPONENT" "java-web-servlet"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.HTTP_URL" "http://localhost:$port/$jspWebappContext/$staticFile"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/$staticFile"
            defaultTags()
          }
        }
      }
    }

    cleanup:
    res.close()

    where:
    staticFile = "common/hello.html"
  }
}

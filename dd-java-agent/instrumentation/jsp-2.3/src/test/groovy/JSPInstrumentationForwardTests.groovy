import com.google.common.io.Files
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.jasper.JasperException
import org.eclipse.jetty.http.HttpStatus
import spock.lang.Shared
import spock.lang.Unroll

class JSPInstrumentationForwardTests extends AgentTestRunner {

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
      JSPInstrumentationForwardTests.getResource("/webapps/jsptest").getPath())

    tomcatServer.start()
    System.out.println(
      "Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + port + "/")
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
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/$forwardFromFileName"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/$forwardFromFileName"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/$forwardFromFileName"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" jspForwardFromClassName
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/$forwardDestFileName"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" jspForwardDestClassName
            "servlet.context" "/$jspWebappContext"
            "jsp.forwardOrigin" "/$forwardFromFileName"
            "jsp.requestURL" baseUrl + "/$forwardDestFileName"
            defaultTags()
          }
        }
        span(3) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/$forwardDestFileName"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.$jspForwardDestClassPrefix$jspForwardDestClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(4) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/$forwardFromFileName"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.$jspForwardFromClassPrefix$jspForwardFromClassName"
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
    forwardTo         | forwardFromFileName                | forwardDestFileName | jspForwardFromClassName   | jspForwardFromClassPrefix | jspForwardDestClassName | jspForwardDestClassPrefix
    "no java jsp"     | "forwards/forwardToNoJavaJsp.jsp"  | "nojava.jsp"        | "forwardToNoJavaJsp_jsp"  | "forwards."               | "nojava_jsp"            | ""
    "normal java jsp" | "forwards/forwardToSimpleJava.jsp" | "common/loop.jsp"   | "forwardToSimpleJava_jsp" | "forwards."               | "loop_jsp"              | "common."
  }

  def "non-erroneous GET forward to plain HTML"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToHtml.jsp"
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
          resourceName "GET /$jspWebappContext/forwards/forwardToHtml.jsp"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/forwards/forwardToHtml.jsp"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/forwards/forwardToHtml.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" "forwardToHtml_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/forwards/forwardToHtml.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToHtml_jsp"
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

  def "non-erroneous GET forwarded to jsp with multiple includes"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToIncludeMulti.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(1) {
      trace(0, 9) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/forwards/forwardToIncludeMulti.jsp"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/forwards/forwardToIncludeMulti.jsp"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/forwards/forwardToIncludeMulti.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" "forwardToIncludeMulti_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/includes/includeMulti.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" "includeMulti_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" baseUrl + "/includes/includeMulti.jsp"
            defaultTags()
          }
        }
        span(3) {
          childOf span(2)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" "javaLoopH2_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" baseUrl + "/includes/includeMulti.jsp"
            defaultTags()
          }
        }
        span(4) {
          childOf span(2)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/common/javaLoopH2.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(5) {
          childOf span(2)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" "javaLoopH2_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" baseUrl + "/includes/includeMulti.jsp"
            defaultTags()
          }
        }
        span(6) {
          childOf span(2)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/common/javaLoopH2.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(7) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/includes/includeMulti.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.includes.includeMulti_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(8) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/forwards/forwardToIncludeMulti.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToIncludeMulti_jsp"
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

  def "non-erroneous GET forward to another forward (2 forwards)"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToJspForward.jsp"
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
          resourceName "GET /$jspWebappContext/forwards/forwardToJspForward.jsp"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/forwards/forwardToJspForward.jsp"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/forwards/forwardToJspForward.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" "forwardToJspForward_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/forwards/forwardToSimpleJava.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" "forwardToSimpleJava_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.forwardOrigin" "/forwards/forwardToJspForward.jsp"
            "jsp.requestURL" baseUrl + "/forwards/forwardToSimpleJava.jsp"
            defaultTags()
          }
        }
        span(3) {
          childOf span(2)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/common/loop.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" "loop_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.forwardOrigin" "/forwards/forwardToJspForward.jsp"
            "jsp.requestURL" baseUrl + "/common/loop.jsp"
            defaultTags()
          }
        }
        span(4) {
          childOf span(2)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/common/loop.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.common.loop_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(5) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/forwards/forwardToSimpleJava.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToSimpleJava_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(6) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/forwards/forwardToJspForward.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToJspForward_jsp"
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

  def "forward to jsp with compile error should not produce a 2nd render span"() {
    setup:
    String reqUrl = baseUrl + "/forwards/forwardToCompileError.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/forwards/forwardToCompileError.jsp"
          spanType DDSpanTypes.HTTP_SERVER
          errored true
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/forwards/forwardToCompileError.jsp"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 500
            errorTags(JasperException, String)
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/forwards/forwardToCompileError.jsp"
          errored true
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" "forwardToCompileError_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            errorTags(JasperException, String)
            defaultTags()
          }
        }
        span(2) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/compileError.jsp"
          errored true
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.compileError_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            errorTags(JasperException, String)
            defaultTags()
          }
        }
        span(3) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/forwards/forwardToCompileError.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToCompileError_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpStatus.INTERNAL_SERVER_ERROR_500

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
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "404"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/forwards/forwardToNonExistent.jsp"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 404
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/forwards/forwardToNonExistent.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "span.origin.type" "forwardToNonExistent_jsp"
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/forwards/forwardToNonExistent.jsp"
          errored false
          tags {
            "component" "jsp-http-servlet"
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToNonExistent_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpStatus.NOT_FOUND_404

    cleanup:
    res.close()
  }
}

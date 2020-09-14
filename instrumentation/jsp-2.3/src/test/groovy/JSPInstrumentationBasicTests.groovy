/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static io.opentelemetry.trace.Span.Kind.SERVER

import com.google.common.io.Files
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.OkHttpUtils
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.trace.attributes.SemanticAttributes
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.jasper.JasperException
import spock.lang.Shared
import spock.lang.Unroll

//TODO should this be HttpServerTest?
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
          operationName expectedOperationName()
          spanKind SERVER
          errored false
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "http://localhost:$port/$jspWebappContext/$jspFileName"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/$jspFileName"
          }
        }
        span(1) {
          childOf span(0)
          operationName "Compile /$jspFileName"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.$jspClassNamePrefix$jspClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          operationName "Render /$jspFileName"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
          }
        }
      }
    }
    res.code() == 200

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
          operationName expectedOperationName()
          spanKind SERVER
          errored false
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "http://localhost:$port/$jspWebappContext/getQuery.jsp?$queryString"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/getQuery.jsp"
          }
        }
        span(1) {
          childOf span(0)
          operationName "Compile /getQuery.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.getQuery_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          operationName "Render /getQuery.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
          }
        }
      }
    }
    res.code() == 200

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
          operationName expectedOperationName()
          spanKind SERVER
          errored false
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "http://localhost:$port/$jspWebappContext/post.jsp"
            "${SemanticAttributes.HTTP_METHOD.key()}" "POST"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/post.jsp"
          }
        }
        span(1) {
          childOf span(0)
          operationName "Compile /post.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.post_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          operationName "Render /post.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
          }
        }
      }
    }
    res.code() == 200

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
          operationName expectedOperationName()
          spanKind SERVER
          errored true
          event(0) {
            eventName(SemanticAttributes.EXCEPTION_EVENT_NAME)
            attributes {
              "${SemanticAttributes.EXCEPTION_TYPE.key()}" { String tagExceptionType ->
                return tagExceptionType == exceptionClass.getName() || tagExceptionType.contains(exceptionClass.getSimpleName())
              }
              "${SemanticAttributes.EXCEPTION_MESSAGE.key()}" { String tagErrorMsg ->
                return errorMessageOptional || tagErrorMsg instanceof String
              }
              "${SemanticAttributes.EXCEPTION_STACKTRACE.key()}" String
            }
          }
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "http://localhost:$port/$jspWebappContext/$jspFileName"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 500
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/$jspFileName"
          }
        }
        span(1) {
          childOf span(0)
          operationName "Compile /$jspFileName"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.$jspClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          operationName "Render /$jspFileName"
          errored true
          event(0) {
            eventName(SemanticAttributes.EXCEPTION_EVENT_NAME)
            attributes {
              "${SemanticAttributes.EXCEPTION_TYPE.key()}" { String tagExceptionType ->
                return tagExceptionType == exceptionClass.getName() || tagExceptionType.contains(exceptionClass.getSimpleName())
              }
              "${SemanticAttributes.EXCEPTION_MESSAGE.key()}" { String tagErrorMsg ->
                return errorMessageOptional || tagErrorMsg instanceof String
              }
              "${SemanticAttributes.EXCEPTION_STACKTRACE.key()}" String
            }
          }
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
          }
        }
      }
    }
    res.code() == 500

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
          operationName expectedOperationName()
          spanKind SERVER
          errored false
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "http://localhost:$port/$jspWebappContext/includes/includeHtml.jsp"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/includes/includeHtml.jsp"
          }
        }
        span(1) {
          childOf span(0)
          operationName "Compile /includes/includeHtml.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.includes.includeHtml_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          operationName "Render /includes/includeHtml.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
          }
        }
      }
    }
    res.code() == 200

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
          operationName expectedOperationName()
          spanKind SERVER
          errored false
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "http://localhost:$port/$jspWebappContext/includes/includeMulti.jsp"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/includes/includeMulti.jsp"
          }
        }
        span(1) {
          childOf span(0)
          operationName "Compile /includes/includeMulti.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.includes.includeMulti_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          operationName "Render /includes/includeMulti.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
          }
        }
        span(3) {
          childOf span(2)
          operationName "Compile /common/javaLoopH2.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(4) {
          childOf span(2)
          operationName "Render /common/javaLoopH2.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
          }
        }
        span(5) {
          childOf span(2)
          operationName "Compile /common/javaLoopH2.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(6) {
          childOf span(2)
          operationName "Render /common/javaLoopH2.jsp"
          errored false
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
          }
        }
      }
    }
    res.code() == 200

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
          operationName expectedOperationName()
          spanKind SERVER
          errored true
          errorEvent(JasperException, String)
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "http://localhost:$port/$jspWebappContext/$jspFileName"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 500
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/$jspFileName"
          }
        }
        span(1) {
          childOf span(0)
          operationName "Compile /$jspFileName"
          errored true
          errorEvent(JasperException, String)
          attributes {
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.$jspClassNamePrefix$jspClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
      }
    }
    res.code() == 500

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
    res.code() == 200
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          parent()
          // serviceName jspWebappContext
          operationName expectedOperationName()
          spanKind SERVER
          // FIXME: this is not a great span name for serving static content.
          // spanName "GET /$jspWebappContext/$staticFile"
          errored false
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "http://localhost:$port/$jspWebappContext/$staticFile"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
            "servlet.context" "/$jspWebappContext"
            "servlet.path" "/$staticFile"
          }
        }
      }
    }

    cleanup:
    res.close()

    where:
    staticFile = "common/hello.html"
  }

  //Simple class name plus method name of the entry point of the given servlet container.
  //"Entry point" here means the first filter or servlet that accepts incoming requests.
  //This will serve as a default name of the SERVER span created for this request.
  protected String expectedOperationName() {
    'ApplicationFilterChain.doFilter'
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.client.WebClient
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.jasper.JasperException
import spock.lang.Shared
import spock.lang.Unroll

import java.nio.file.Files

import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.api.trace.StatusCode.UNSET

class JspInstrumentationForwardTests extends AgentInstrumentationSpecification {

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

  @Shared
  WebClient client

  def setupSpec() {
    baseDir = Files.createTempDirectory("jsp").toFile()
    baseDir.deleteOnExit()

    port = PortUtils.findOpenPort()

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
    client = WebClient.of(baseUrl)

    appContext = tomcatServer.addWebapp("/$jspWebappContext",
      JspInstrumentationForwardTests.getResource("/webapps/jsptest").getPath())

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
    when:
    AggregatedHttpResponse res = client.get("/$forwardFromFileName").aggregate().join()

    then:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          hasNoParent()
          name "/$jspWebappContext/$forwardFromFileName"
          kind SERVER
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_SCHEME.key}" "http"
            "${SemanticAttributes.HTTP_HOST.key}" "localhost:$port"
            "${SemanticAttributes.HTTP_TARGET.key}" "/$jspWebappContext/$forwardFromFileName"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
            "${SemanticAttributes.HTTP_SERVER_NAME}" String
            "${SemanticAttributes.NET_TRANSPORT}" IP_TCP
          }
        }
        span(1) {
          childOf span(0)
          name "Compile /$forwardFromFileName"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.$jspForwardFromClassPrefix$jspForwardFromClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          name "Render /$forwardFromFileName"
          attributes {
            "jsp.requestURL" "${baseUrl}/$forwardFromFileName"
          }
        }
        span(3) {
          childOf span(2)
          name "Compile /$forwardDestFileName"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.$jspForwardDestClassPrefix$jspForwardDestClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(4) {
          childOf span(2)
          name "Render /$forwardDestFileName"
          attributes {
            "jsp.forwardOrigin" "/$forwardFromFileName"
            "jsp.requestURL" "${baseUrl}/$forwardDestFileName"
          }
        }
      }
    }
    res.status().code() == 200

    where:
    forwardTo         | forwardFromFileName                | forwardDestFileName | jspForwardFromClassName   | jspForwardFromClassPrefix | jspForwardDestClassName | jspForwardDestClassPrefix
    "no java jsp"     | "forwards/forwardToNoJavaJsp.jsp"  | "nojava.jsp"        | "forwardToNoJavaJsp_jsp"  | "forwards."               | "nojava_jsp"            | ""
    "normal java jsp" | "forwards/forwardToSimpleJava.jsp" | "common/loop.jsp"   | "forwardToSimpleJava_jsp" | "forwards."               | "loop_jsp"              | "common."
  }

  def "non-erroneous GET forward to plain HTML"() {
    when:
    AggregatedHttpResponse res = client.get("/forwards/forwardToHtml.jsp").aggregate().join()

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          hasNoParent()
          name "/$jspWebappContext/forwards/forwardToHtml.jsp"
          kind SERVER
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_SCHEME.key}" "http"
            "${SemanticAttributes.HTTP_HOST.key}" "localhost:$port"
            "${SemanticAttributes.HTTP_TARGET.key}" "/$jspWebappContext/forwards/forwardToHtml.jsp"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
            "${SemanticAttributes.HTTP_SERVER_NAME}" String
            "${SemanticAttributes.NET_TRANSPORT}" IP_TCP
          }
        }
        span(1) {
          childOf span(0)
          name "Compile /forwards/forwardToHtml.jsp"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToHtml_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          name "Render /forwards/forwardToHtml.jsp"
          attributes {
            "jsp.requestURL" "${baseUrl}/forwards/forwardToHtml.jsp"
          }
        }
      }
    }
    res.status().code() == 200
  }

  def "non-erroneous GET forwarded to jsp with multiple includes"() {
    when:
    AggregatedHttpResponse res = client.get("/forwards/forwardToIncludeMulti.jsp").aggregate().join()

    then:
    assertTraces(1) {
      trace(0, 9) {
        span(0) {
          hasNoParent()
          name "/$jspWebappContext/forwards/forwardToIncludeMulti.jsp"
          kind SERVER
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_SCHEME.key}" "http"
            "${SemanticAttributes.HTTP_HOST.key}" "localhost:$port"
            "${SemanticAttributes.HTTP_TARGET.key}" "/$jspWebappContext/forwards/forwardToIncludeMulti.jsp"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
            "${SemanticAttributes.HTTP_SERVER_NAME}" String
            "${SemanticAttributes.NET_TRANSPORT}" IP_TCP
          }
        }
        span(1) {
          childOf span(0)
          name "Compile /forwards/forwardToIncludeMulti.jsp"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToIncludeMulti_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          name "Render /forwards/forwardToIncludeMulti.jsp"
          attributes {
            "jsp.requestURL" "${baseUrl}/forwards/forwardToIncludeMulti.jsp"
          }
        }
        span(3) {
          childOf span(2)
          name "Compile /includes/includeMulti.jsp"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.includes.includeMulti_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(4) {
          childOf span(2)
          name "Render /includes/includeMulti.jsp"
          attributes {
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" "${baseUrl}/includes/includeMulti.jsp"
          }
        }
        span(5) {
          childOf span(4)
          name "Compile /common/javaLoopH2.jsp"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(6) {
          childOf span(4)
          name "Render /common/javaLoopH2.jsp"
          attributes {
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" "${baseUrl}/includes/includeMulti.jsp"
          }
        }
        span(7) {
          childOf span(4)
          name "Compile /common/javaLoopH2.jsp"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(8) {
          childOf span(4)
          name "Render /common/javaLoopH2.jsp"
          attributes {
            "jsp.forwardOrigin" "/forwards/forwardToIncludeMulti.jsp"
            "jsp.requestURL" "${baseUrl}/includes/includeMulti.jsp"
          }
        }
      }
    }
    res.status().code() == 200
  }

  def "non-erroneous GET forward to another forward (2 forwards)"() {
    when:
    AggregatedHttpResponse res = client.get("/forwards/forwardToJspForward.jsp").aggregate().join()

    then:
    assertTraces(1) {
      trace(0, 7) {
        span(0) {
          hasNoParent()
          name "/$jspWebappContext/forwards/forwardToJspForward.jsp"
          kind SERVER
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_SCHEME.key}" "http"
            "${SemanticAttributes.HTTP_HOST.key}" "localhost:$port"
            "${SemanticAttributes.HTTP_TARGET.key}" "/$jspWebappContext/forwards/forwardToJspForward.jsp"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
            "${SemanticAttributes.HTTP_SERVER_NAME}" String
            "${SemanticAttributes.NET_TRANSPORT}" IP_TCP
          }
        }
        span(1) {
          childOf span(0)
          name "Compile /forwards/forwardToJspForward.jsp"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToJspForward_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          name "Render /forwards/forwardToJspForward.jsp"
          attributes {
            "jsp.requestURL" "${baseUrl}/forwards/forwardToJspForward.jsp"
          }
        }
        span(3) {
          childOf span(2)
          name "Compile /forwards/forwardToSimpleJava.jsp"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToSimpleJava_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(4) {
          childOf span(2)
          name "Render /forwards/forwardToSimpleJava.jsp"
          attributes {
            "jsp.forwardOrigin" "/forwards/forwardToJspForward.jsp"
            "jsp.requestURL" "${baseUrl}/forwards/forwardToSimpleJava.jsp"
          }
        }
        span(5) {
          childOf span(4)
          name "Compile /common/loop.jsp"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.common.loop_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(6) {
          childOf span(4)
          name "Render /common/loop.jsp"
          attributes {
            "jsp.forwardOrigin" "/forwards/forwardToJspForward.jsp"
            "jsp.requestURL" "${baseUrl}/common/loop.jsp"
          }
        }
      }
    }
    res.status().code() == 200
  }

  def "forward to jsp with compile error should not produce a 2nd render span"() {
    when:
    AggregatedHttpResponse res = client.get("/forwards/forwardToCompileError.jsp").aggregate().join()

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          hasNoParent()
          name "/$jspWebappContext/forwards/forwardToCompileError.jsp"
          kind SERVER
          status ERROR
          errorEvent(JasperException, String)
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_SCHEME.key}" "http"
            "${SemanticAttributes.HTTP_HOST.key}" "localhost:$port"
            "${SemanticAttributes.HTTP_TARGET.key}" "/$jspWebappContext/forwards/forwardToCompileError.jsp"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 500
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
            "${SemanticAttributes.HTTP_SERVER_NAME}" String
            "${SemanticAttributes.NET_TRANSPORT}" IP_TCP
          }
        }
        span(1) {
          childOf span(0)
          name "Compile /forwards/forwardToCompileError.jsp"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToCompileError_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          name "Render /forwards/forwardToCompileError.jsp"
          status ERROR
          errorEvent(JasperException, String)
          attributes {
            "jsp.requestURL" "${baseUrl}/forwards/forwardToCompileError.jsp"
          }
        }
        span(3) {
          childOf span(2)
          name "Compile /compileError.jsp"
          status ERROR
          errorEvent(JasperException, String)
          attributes {
            "jsp.classFQCN" "org.apache.jsp.compileError_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
      }
    }
    res.status().code() == 500
  }

  def "forward to non existent jsp should be 404"() {
    when:
    AggregatedHttpResponse res = client.get("/forwards/forwardToNonExistent.jsp").aggregate().join()

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          hasNoParent()
          name "/$jspWebappContext/forwards/forwardToNonExistent.jsp"
          kind SERVER
          status UNSET
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_SCHEME.key}" "http"
            "${SemanticAttributes.HTTP_HOST.key}" "localhost:$port"
            "${SemanticAttributes.HTTP_TARGET.key}" "/$jspWebappContext/forwards/forwardToNonExistent.jsp"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 404
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
            "${SemanticAttributes.HTTP_SERVER_NAME}" String
            "${SemanticAttributes.NET_TRANSPORT}" IP_TCP
          }
        }
        span(1) {
          childOf span(0)
          name "Compile /forwards/forwardToNonExistent.jsp"
          attributes {
            "jsp.classFQCN" "org.apache.jsp.forwards.forwardToNonExistent_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
          }
        }
        span(2) {
          childOf span(0)
          name "Render /forwards/forwardToNonExistent.jsp"
          attributes {
            "jsp.requestURL" "${baseUrl}/forwards/forwardToNonExistent.jsp"
          }
        }
        span(3) {
          childOf span(2)
          name "ResponseFacade.sendError"
        }
      }
    }
    res.status().code() == 404
  }
}

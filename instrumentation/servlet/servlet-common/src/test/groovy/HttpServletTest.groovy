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

import groovy.servlet.AbstractHttpServlet
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

class HttpServletTest extends AgentTestRunner {
  static {
    ConfigUtils.updateConfig {
      System.setProperty("ota.integration.servlet-service.enabled", "true")
    }
  }

  def specCleanup() {
    ConfigUtils.updateConfig {
      System.clearProperty("ota.integration.servlet-service.enabled")
    }
  }


  def req = Mock(HttpServletRequest) {
    getMethod() >> "GET"
    getProtocol() >> "TEST"
  }
  def resp = Mock(HttpServletResponse)

  def "test service no-parent"() {
    when:
    servlet.service(req, resp)

    then:
    assertTraces(0) {}

    where:
    servlet = new TestServlet()
  }

  def "test service with parent"() {
    when:
    runUnderTrace("parent") {
      servlet.service(req, resp)
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName "HttpServlet.service"
          childOf span(0)
          attributes {
          }
        }
        span(2) {
          operationName "${expectedSpanName}.doGet"
          childOf span(1)
          attributes {
          }
        }
      }
    }

    where:
    servlet << [new TestServlet(), new TestServlet() {
      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      }
    }]

    expectedSpanName = servlet.class.anonymousClass ? servlet.class.name : servlet.class.simpleName
  }

  def "test service exception"() {
    setup:
    def ex = new Exception("some error")
    def servlet = new TestServlet() {
      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        throw ex
      }
    }

    when:
    runUnderTrace("parent") {
      servlet.service(req, resp)
    }

    then:
    def th = thrown(Exception)
    th == ex

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent", null, ex)
        span(1) {
          operationName "HttpServlet.service"
          childOf span(0)
          errored true
          errorEvent(ex.class, ex.message)
        }
        span(2) {
          operationName "${servlet.class.name}.doGet"
          childOf span(1)
          errored true
          errorEvent(ex.class, ex.message)
        }
      }
    }
  }

  static class TestServlet extends AbstractHttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    }
  }
}

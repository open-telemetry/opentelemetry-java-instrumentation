/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

class FilterTest extends AgentTestRunner {
  static {
    ConfigUtils.updateConfig {
      System.setProperty("otel.integration.servlet-filter.enabled", "true")
    }
  }

  def specCleanup() {
    ConfigUtils.updateConfig {
      System.clearProperty("otel.integration.servlet-filter.enabled")
    }
  }


  def "test doFilter no-parent"() {
    when:
    filter.doFilter(null, null, null)

    then:
    assertTraces(0) {}

    where:
    filter = new TestFilter()
  }

  def "test doFilter with parent"() {
    when:
    runUnderTrace("parent") {
      filter.doFilter(null, null, null)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName "${filter.class.simpleName}.doFilter"
          childOf span(0)
          attributes {
          }
        }
      }
    }

    where:
    filter << [new TestFilter(), new TestFilter() {}]
  }

  def "test doFilter exception"() {
    setup:
    def ex = new Exception("some error")
    def filter = new TestFilter() {
      @Override
      void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) {
        throw ex
      }
    }

    when:
    runUnderTrace("parent") {
      filter.doFilter(null, null, null)
    }

    then:
    def th = thrown(Exception)
    th == ex

    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent", null, ex)
        span(1) {
          operationName "${filter.class.simpleName}.doFilter"
          childOf span(0)
          errored true
          errorEvent(ex.class, ex.message)
        }
      }
    }
  }

  static class TestFilter implements Filter {

    @Override
    void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
    }

    @Override
    void destroy() {
    }
  }
}

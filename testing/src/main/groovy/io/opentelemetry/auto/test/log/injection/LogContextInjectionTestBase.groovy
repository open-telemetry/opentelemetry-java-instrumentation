/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.test.log.injection


import io.opentelemetry.OpenTelemetry
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.context.Scope
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.Tracer

import java.util.concurrent.atomic.AtomicReference

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith

/**
 * This class represents the standard test cases that new logging library integrations MUST
 * satisfy in order to support log injection.
 */
abstract class LogContextInjectionTestBase extends AgentTestRunner {

  final Tracer tracer = OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.test")

  /**
   * Set in the framework-specific context the given value at the given key
   */
  abstract put(String key, Object value)

  /**
   * Get from the framework-specific context the value at the given key
   */
  abstract get(String key)

  static {
    ConfigUtils.updateConfig {
      System.setProperty("ota.logs.injection", "true")
    }
  }

  def "Log context shows trace and span ids for active scope"() {
    when:
    put("foo", "bar")
    Span rootSpan = TEST_TRACER.spanBuilder("root").startSpan()
    Scope rootScope = currentContextWith(rootSpan)

    then:
    get("ot.trace_id") == tracer.getCurrentSpan().getContext().getTraceId().toLowerBase16()
    get("ot.span_id") == tracer.getCurrentSpan().getContext().getSpanId().toLowerBase16()
    get("foo") == "bar"

    when:
    Span childSpan = TEST_TRACER.spanBuilder("child").startSpan()
    Scope childScope = currentContextWith(childSpan)

    then:
    get("ot.trace_id") == tracer.getCurrentSpan().getContext().getTraceId().toLowerBase16()
    get("ot.span_id") == tracer.getCurrentSpan().getContext().getSpanId().toLowerBase16()
    get("foo") == "bar"

    when:
    childSpan.end()
    childScope.close()

    then:
    get("ot.trace_id") == tracer.getCurrentSpan().getContext().getTraceId().toLowerBase16()
    get("ot.span_id") == tracer.getCurrentSpan().getContext().getSpanId().toLowerBase16()
    get("foo") == "bar"

    when:
    rootSpan.end()
    rootScope.close()

    then:
    get("ot.trace_id") == null
    get("ot.span_id") == null
    get("foo") == "bar"
  }

  def "Log context is scoped by thread"() {
    setup:
    ConfigUtils.updateConfig {
      System.setProperty("ota.logs.injection", "true")
    }
    AtomicReference<String> thread1TraceId = new AtomicReference<>()
    AtomicReference<String> thread2TraceId = new AtomicReference<>()

    final Thread thread1 = new Thread() {
      @Override
      void run() {
        // no trace in scope
        thread1TraceId.set(get("ot.trace_id"))
      }
    }

    final Thread thread2 = new Thread() {
      @Override
      void run() {
        // other trace in scope
        final Span thread2Span = TEST_TRACER.spanBuilder("root2").startSpan()
        final Scope thread2Scope = currentContextWith(thread2Span)
        try {
          thread2TraceId.set(get("ot.trace_id"))
        } finally {
          thread2Span.end()
          thread2Scope.close()
        }
      }
    }
    final Span mainSpan = TEST_TRACER.spanBuilder("root").startSpan()
    final Scope mainScope = currentContextWith(mainSpan)
    thread1.start()
    thread2.start()
    final String mainThreadTraceId = get("ot.trace_id")
    final String expectedMainThreadTraceId = tracer.getCurrentSpan().getContext().getTraceId().toLowerBase16()

    thread1.join()
    thread2.join()

    expect:
    mainThreadTraceId == expectedMainThreadTraceId
    thread1TraceId.get() == null
    thread2TraceId.get() != null
    thread2TraceId.get() != mainThreadTraceId

    cleanup:
    mainSpan?.end()
    mainScope?.close()
  }
}

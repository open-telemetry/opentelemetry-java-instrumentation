/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.async

import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.tracer.BaseTracer
import spock.lang.Specification

class SynchronousMethodSpanStrategyTest extends Specification {
  BaseTracer tracer

  Context context

  def underTest = SynchronousMethodSpanStrategy.INSTANCE

  void setup() {
    tracer = Mock()
    context = Mock()
  }

  def "ends span on any result"() {
    when:
    underTest.end(tracer, context, "any result")

    then:
    1 * tracer.end(context)
  }

  def "ends span on null result"() {
    when:
    underTest.end(tracer, context, null)

    then:
    1 * tracer.end(context)
  }
}

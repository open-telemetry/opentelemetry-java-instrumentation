/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.tracer.BaseTracer
import io.opentelemetry.instrumentation.guava.GuavaAsyncSpanEndStrategy
import spock.lang.Specification

class GuavaAsyncSpanEndStrategyTest extends Specification  {
  BaseTracer tracer

  Context context

  Span span

  def underTest = GuavaAsyncSpanEndStrategy.create()

  def underTestWithExperimentalAttributes = GuavaAsyncSpanEndStrategy.newBuilder()
    .setCaptureExperimentalSpanAttributes(true)
    .build()

  void setup() {
    tracer = Mock()
    context = Mock()
    span = Mock()
    span.storeInContext(_) >> { callRealMethod() }
  }

  def "ListenableFuture is supported"() {
    expect:
    underTest.supports(ListenableFuture)
  }

  def "SettableFuture is also supported"() {
    expect:
    underTest.supports(SettableFuture)
  }

  def "ends span on already done future"() {
    when:
    underTest.end(tracer, context, Futures.immediateFuture("Value"))

    then:
    1 * tracer.end(context)
  }

  def "ends span on already failed future"() {
    given:
    def exception = new IllegalStateException()

    when:
    underTest.end(tracer, context, Futures.immediateFailedFuture(exception))

    then:
    1 * tracer.endExceptionally(context, { it.getCause() == exception })
  }

  def "ends span on eventually done future"() {
    given:
    def future = SettableFuture.<String>create()

    when:
    underTest.end(tracer, context, future)

    then:
    0 * tracer._

    when:
    future.set("Value")

    then:
    1 * tracer.end(context)
  }

  def "ends span on eventually failed future"() {
    given:
    def future = SettableFuture.<String>create()
    def exception = new IllegalStateException()

    when:
    underTest.end(tracer, context, future)

    then:
    0 * tracer._

    when:
    future.setException(exception)

    then:
    1 * tracer.endExceptionally(context, { it.getCause() == exception })
  }

  def "ends span on eventually canceled future"() {
    given:
    def future = SettableFuture.<String>create()
    def context = span.storeInContext(Context.root())

    when:
    underTest.end(tracer, context, future)

    then:
    0 * tracer._

    when:
    future.cancel(true)

    then:
    1 * tracer.end(context)
    0 * span.setAttribute(_)
  }

  def "ends span on eventually canceled future and capturing experimental span attributes"() {
    given:
    def future = SettableFuture.<String>create()
    def context = span.storeInContext(Context.root())

    when:
    underTestWithExperimentalAttributes.end(tracer, context, future)

    then:
    0 * tracer._

    when:
    future.cancel(true)

    then:
    1 * tracer.end(context)
    1 * span.setAttribute({ it.getKey() == "guava.canceled" }, true)
  }
}

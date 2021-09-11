/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.guava.GuavaAsyncOperationEndStrategy
import spock.lang.Specification

class GuavaAsyncOperationEndStrategyTest extends Specification {
  String request = "request"
  String response = "response"

  Instrumenter<String, String> instrumenter

  Context context

  Span span

  def underTest = GuavaAsyncOperationEndStrategy.create()

  def underTestWithExperimentalAttributes = GuavaAsyncOperationEndStrategy.newBuilder()
    .setCaptureExperimentalSpanAttributes(true)
    .build()

  void setup() {
    instrumenter = Mock()
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
    underTest.end(instrumenter, context, request, Futures.immediateFuture(response), String)

    then:
    1 * instrumenter.end(context, request, response, null)
  }

  def "ends span on already failed future"() {
    given:
    def exception = new IllegalStateException()

    when:
    underTest.end(instrumenter, context, request, Futures.immediateFailedFuture(exception), String)

    then:
    1 * instrumenter.end(context, request, null, { it.getCause() == exception })
  }

  def "ends span on eventually done future"() {
    given:
    def future = SettableFuture.<String> create()

    when:
    underTest.end(instrumenter, context, request, future, String)

    then:
    0 * instrumenter._

    when:
    future.set(response)

    then:
    1 * instrumenter.end(context, request, response, null)
  }

  def "ends span on eventually failed future"() {
    given:
    def future = SettableFuture.<String> create()
    def exception = new IllegalStateException()

    when:
    underTest.end(instrumenter, context, request, future, String)

    then:
    0 * instrumenter._

    when:
    future.setException(exception)

    then:
    1 * instrumenter.end(context, request, null, { it.getCause() == exception })
  }

  def "ends span on eventually canceled future"() {
    given:
    def future = SettableFuture.<String> create()
    def context = span.storeInContext(Context.root())

    when:
    underTest.end(instrumenter, context, request, future, String)

    then:
    0 * instrumenter._

    when:
    future.cancel(true)

    then:
    1 * instrumenter.end(context, request, null, null)
    0 * span.setAttribute(_)
  }

  def "ends span on eventually canceled future and capturing experimental span attributes"() {
    given:
    def future = SettableFuture.<String> create()
    def context = span.storeInContext(Context.root())

    when:
    underTestWithExperimentalAttributes.end(instrumenter, context, request, future, String)

    then:
    0 * instrumenter._

    when:
    future.cancel(true)

    then:
    1 * instrumenter.end(context, request, null, null)
    1 * span.setAttribute({ it.getKey() == "guava.canceled" }, true)
  }
}

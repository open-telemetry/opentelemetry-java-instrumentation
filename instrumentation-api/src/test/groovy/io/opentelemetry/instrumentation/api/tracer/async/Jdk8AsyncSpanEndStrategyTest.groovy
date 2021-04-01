/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.async

import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.tracer.BaseTracer
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class Jdk8AsyncSpanEndStrategyTest extends Specification {
  BaseTracer tracer

  Context context

  def underTest = Jdk8AsyncSpanEndStrategy.INSTANCE

  void setup() {
    tracer = Mock()
    context = Mock()
  }

  def "ends span on completed future"() {
    when:
    underTest.end(tracer, context, CompletableFuture.completedFuture("completed"))

    then:
    1 * tracer.end(context)
  }

  def "ends span exceptionally on failed future"() {
    given:
    def exception = new CompletionException()
    def future = new CompletableFuture<String>()
    future.completeExceptionally(exception)

    when:
    underTest.end(tracer, context, future)

    then:
    1 * tracer.endExceptionally(context, exception)
  }

  def "ends span on future when complete"() {
    def future = new CompletableFuture<String>()

    when:
    underTest.end(tracer, context, future)

    then:
    0 * tracer._

    when:
    future.complete("completed")

    then:
    1 * tracer.end(context)
  }

  def "ends span exceptionally on future when completed exceptionally"() {
    def future = new CompletableFuture<String>()
    def exception = new Exception()

    when:
    underTest.end(tracer, context, future)

    then:
    0 * tracer._

    when:
    future.completeExceptionally(exception)

    then:
    1 * tracer.endExceptionally(context, exception)
  }
}

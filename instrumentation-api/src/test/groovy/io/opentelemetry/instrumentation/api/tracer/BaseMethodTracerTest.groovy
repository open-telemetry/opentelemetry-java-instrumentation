/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer

import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.tracer.async.MethodSpanStrategy
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class BaseMethodTracerTest extends Specification {

  BaseMethodTracer tracer

  void setup() {
    tracer = new BaseMethodTracer() {
      @Override
      protected String getInstrumentationName() {
        return "BaseMethodTracerTest"
      }
    }
  }

  def "resolves method strategy and stores in returned Context"() {
    given:
    def strategy = MethodSpanStrategy.forCompletableFuture()
    def method = TestMethods.getMethod("completableFuture")

    when:
    def context = tracer.withMethodSpanStrategy(Context.root(), method)

    then:
    context.get(MethodSpanStrategy.CONTEXT_KEY) == strategy
  }

  def "ends span through method strategy in Context"() {
    given:
    MethodSpanStrategy strategy = Mock()
    def context = Context.root().with(MethodSpanStrategy.CONTEXT_KEY, strategy)

    when:
    tracer.end(context, "Result")

    then:
    1 * strategy.end(tracer, context, "Result")
  }

  class TestMethods {
    CompletableFuture<String> completableFuture() {
      return CompletableFuture.completedFuture("Value")
    }
  }
}

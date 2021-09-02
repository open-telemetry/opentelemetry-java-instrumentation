/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import spock.lang.Specification

class AddThreadDetailsSpanProcessorTest extends Specification {
  def span = Mock(ReadWriteSpan)

  def processor = new AddThreadDetailsSpanProcessor()

  def "should require onStart call"() {
    expect:
    processor.isStartRequired()
  }

  def "should set thread attributes on span start"() {
    given:
    def currentThreadName = Thread.currentThread().name
    def currentThreadId = Thread.currentThread().id

    when:
    processor.onStart(Context.root(), span)

    then:
    1 * span.setAttribute(SemanticAttributes.THREAD_ID, currentThreadId)
    1 * span.setAttribute(SemanticAttributes.THREAD_NAME, currentThreadName)
  }
}

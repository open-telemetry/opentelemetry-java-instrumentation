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

package io.opentelemetry.instrumentation.api.decorator

import io.grpc.Context
import io.opentelemetry.OpenTelemetry
import io.opentelemetry.context.ContextUtils
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.Tracer
import io.opentelemetry.trace.TracingContextUtils

class ClientDecoratorTest extends BaseDecoratorTest {

  private static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto")

  def "test afterStart"() {
    setup:
    def decorator = newDecorator((String) serviceName)

    when:
    decorator.afterStart(span)

    then:
    _ * span.setAttribute(_, _) // Want to allow other calls from child implementations.
    0 * _

    where:
    serviceName << ["test-service", "other-service", null]
  }

  def "test beforeFinish"() {
    when:
    newDecorator("test-service").beforeFinish(span)

    then:
    0 * _
  }

  def "test getOrCreateSpan when no existing client span"() {
    when:
    def span = ClientDecorator.getOrCreateSpan("test", TRACER)

    then:
    assert span.getContext().isValid()
  }

  def "test getOrCreateSpan when existing client span"() {
    setup:
    def existing = ClientDecorator.getOrCreateSpan("existing", TRACER)
    def scope = ContextUtils.withScopedContext(ClientDecorator.currentContextWith(existing))

    when:
    def span = ClientDecorator.getOrCreateSpan("test", TRACER)

    then:
    assert !span.getContext().isValid()

    cleanup:
    scope.close()
  }

  def "test getOrCreateSpan internal after client span"() {
    setup:
    def client = ClientDecorator.getOrCreateSpan("existing", TRACER)
    def scope = ContextUtils.withScopedContext(ClientDecorator.currentContextWith(client))

    when:
    def internal = TRACER.spanBuilder("internal").setSpanKind(Span.Kind.INTERNAL).startSpan()
    def scope2 = TracingContextUtils.currentContextWith(internal)

    then:
    assert internal.getContext().isValid()
    assert ClientDecorator.CONTEXT_CLIENT_SPAN_KEY.get(Context.current()) == client
    assert TracingContextUtils.getSpan(Context.current()) == internal

    cleanup:
    scope2.close()
    scope.close()
  }

  def "test currentContextWith"() {
    setup:
    def span = ClientDecorator.getOrCreateSpan("test", TRACER)

    when:
    def context = ClientDecorator.currentContextWith(span)

    then:
    assert ClientDecorator.CONTEXT_CLIENT_SPAN_KEY.get(context) == span
    assert TracingContextUtils.getSpan(context) == span
  }

  @Override
  def newDecorator() {
    return newDecorator("test-service")
  }

  def newDecorator(String serviceName) {
    return new ClientDecorator() {
    }
  }
}

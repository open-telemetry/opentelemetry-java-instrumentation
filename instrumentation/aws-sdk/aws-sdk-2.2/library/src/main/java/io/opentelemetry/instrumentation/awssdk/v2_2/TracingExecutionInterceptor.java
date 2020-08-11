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

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkClientTracer.TRACER;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/** AWS request execution interceptor */
final class TracingExecutionInterceptor implements ExecutionInterceptor {

  static final ExecutionAttribute<Span> SPAN_ATTRIBUTE =
      new ExecutionAttribute<>("io.opentelemetry.auto.Span");

  private final Kind kind;

  TracingExecutionInterceptor(Kind kind) {
    this.kind = kind;
  }

  @Override
  public void beforeExecution(
      final Context.BeforeExecution context, final ExecutionAttributes executionAttributes) {
    Span span = TRACER.getOrCreateSpan(TRACER.spanName(executionAttributes), AwsSdk.tracer());
    executionAttributes.putAttribute(SPAN_ATTRIBUTE, span);
  }

  @Override
  public void afterMarshalling(
      final Context.AfterMarshalling context, final ExecutionAttributes executionAttributes) {
    Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span != null) {
      TRACER.onRequest(span, context.httpRequest());
      TRACER.onSdkRequest(span, context.request());
      TRACER.onAttributes(span, executionAttributes);
    }
  }

  @Override
  public void afterExecution(
      final Context.AfterExecution context, final ExecutionAttributes executionAttributes) {
    Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span != null) {
      executionAttributes.putAttribute(SPAN_ATTRIBUTE, null);
      TRACER.afterExecution(span, context.httpRequest());
      TRACER.onSdkResponse(span, context.response());
      TRACER.end(span, context.httpResponse());
    }
  }

  @Override
  public void onExecutionFailure(
      final Context.FailedExecution context, final ExecutionAttributes executionAttributes) {
    Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span != null) {
      executionAttributes.putAttribute(SPAN_ATTRIBUTE, null);
      TRACER.endExceptionally(span, context.exception());
    }
  }
}

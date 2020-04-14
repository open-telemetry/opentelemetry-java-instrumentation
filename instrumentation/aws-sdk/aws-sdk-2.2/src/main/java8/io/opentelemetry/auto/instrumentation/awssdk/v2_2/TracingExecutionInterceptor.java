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
package io.opentelemetry.auto.instrumentation.awssdk.v2_2;

import static io.opentelemetry.auto.instrumentation.awssdk.v2_2.AwsSdkClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.awssdk.v2_2.AwsSdkClientDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.util.function.Consumer;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/** AWS request execution interceptor */
public class TracingExecutionInterceptor implements ExecutionInterceptor {

  public static class ScopeHolder {
    public static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
  }

  // Note: it looks like this lambda doesn't get generated as a separate class file so we do not
  // need to inject helper for it.
  private static final Consumer<ClientOverrideConfiguration.Builder>
      OVERRIDE_CONFIGURATION_CONSUMER =
          builder -> builder.addExecutionInterceptor(new TracingExecutionInterceptor());

  private static final ExecutionAttribute<Span> SPAN_ATTRIBUTE =
      new ExecutionAttribute<>("io.opentelemetry.auto.Span");

  @Override
  public void beforeExecution(
      final Context.BeforeExecution context, final ExecutionAttributes executionAttributes) {
    final Span span =
        TRACER.spanBuilder(DECORATE.spanName(executionAttributes)).setSpanKind(CLIENT).startSpan();
    try (final Scope scope = currentContextWith(span)) {
      DECORATE.afterStart(span);
      executionAttributes.putAttribute(SPAN_ATTRIBUTE, span);
    }
  }

  @Override
  public void afterMarshalling(
      final Context.AfterMarshalling context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);

    DECORATE.onRequest(span, context.httpRequest());
    DECORATE.onSdkRequest(span, context.request());
    DECORATE.onAttributes(span, executionAttributes);
  }

  @Override
  public void beforeTransmission(
      final Context.BeforeTransmission context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);

    // This scope will be closed by AwsHttpClientInstrumentation since ExecutionInterceptor API
    // doesn't provide a way to run code in the same thread after transmission has been scheduled.
    ScopeHolder.CURRENT.set(currentContextWith(span));
  }

  @Override
  public void afterExecution(
      final Context.AfterExecution context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span != null) {
      executionAttributes.putAttribute(SPAN_ATTRIBUTE, null);
      // Call onResponse on both types of responses:
      DECORATE.onResponse(span, context.response());
      DECORATE.onResponse(span, context.httpResponse());
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void onExecutionFailure(
      final Context.FailedExecution context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span != null) {
      executionAttributes.putAttribute(SPAN_ATTRIBUTE, null);
      DECORATE.onError(span, context.exception());
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  /**
   * We keep this method here because it references Java8 classes and we would like to avoid
   * compiling this for instrumentation code that should load into Java7.
   */
  public static void overrideConfiguration(final SdkClientBuilder client) {
    client.overrideConfiguration(OVERRIDE_CONFIGURATION_CONSUMER);
  }

  public static void muzzleCheck() {
    // Noop
  }
}

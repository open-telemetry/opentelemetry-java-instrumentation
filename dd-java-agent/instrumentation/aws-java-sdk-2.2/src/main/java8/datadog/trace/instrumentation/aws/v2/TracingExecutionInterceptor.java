package datadog.trace.instrumentation.aws.v2;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.aws.v2.AwsSdkClientDecorator.DECORATE;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.function.Consumer;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/** AWS request execution interceptor */
public class TracingExecutionInterceptor implements ExecutionInterceptor {

  // Note: it looks like this lambda doesn't get generated as a separate class file so we do not
  // need to inject helper for it.
  private static final Consumer<ClientOverrideConfiguration.Builder>
      OVERRIDE_CONFIGURATION_CONSUMER =
          builder -> builder.addExecutionInterceptor(new TracingExecutionInterceptor());

  private static final ExecutionAttribute<AgentSpan> SPAN_ATTRIBUTE =
      new ExecutionAttribute<>("DatadogSpan");

  @Override
  public void beforeExecution(
      final Context.BeforeExecution context, final ExecutionAttributes executionAttributes) {
    final AgentSpan span = startSpan("aws.http");
    try (final AgentScope scope = activateSpan(span, false)) {
      DECORATE.afterStart(span);
      executionAttributes.putAttribute(SPAN_ATTRIBUTE, span);
    }
  }

  @Override
  public void afterMarshalling(
      final Context.AfterMarshalling context, final ExecutionAttributes executionAttributes) {
    final AgentSpan span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);

    DECORATE.onRequest(span, context.httpRequest());
    DECORATE.onSdkRequest(span, context.request());
    DECORATE.onAttributes(span, executionAttributes);
  }

  @Override
  public void beforeTransmission(
      final Context.BeforeTransmission context, final ExecutionAttributes executionAttributes) {
    final AgentSpan span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);

    // This scope will be closed by AwsHttpClientInstrumentation since ExecutionInterceptor API
    // doesn't provide a way to run code in the same thread after transmission has been scheduled.
    final AgentScope scope = activateSpan(span, false);
    scope.setAsyncPropagation(true);
  }

  @Override
  public void afterExecution(
      final Context.AfterExecution context, final ExecutionAttributes executionAttributes) {
    final AgentSpan span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span != null) {
      executionAttributes.putAttribute(SPAN_ATTRIBUTE, null);
      // Call onResponse on both types of responses:
      DECORATE.onResponse(span, context.response());
      DECORATE.onResponse(span, context.httpResponse());
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  @Override
  public void onExecutionFailure(
      final Context.FailedExecution context, final ExecutionAttributes executionAttributes) {
    final AgentSpan span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    if (span != null) {
      executionAttributes.putAttribute(SPAN_ATTRIBUTE, null);
      DECORATE.onError(span, context.exception());
      DECORATE.beforeFinish(span);
      span.finish();
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

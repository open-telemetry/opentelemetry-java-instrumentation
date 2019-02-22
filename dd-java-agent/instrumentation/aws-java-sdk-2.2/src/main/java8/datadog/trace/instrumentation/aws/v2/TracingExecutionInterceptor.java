package datadog.trace.instrumentation.aws.v2;

import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.util.GlobalTracer;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;

/** AWS request execution interceptor */
public class TracingExecutionInterceptor implements ExecutionInterceptor {

  // Note: it looks like this lambda doesn't get generated as a separate class file so we do not
  // need to inject helper for it.
  private static final Consumer<ClientOverrideConfiguration.Builder>
      OVERRIDE_CONFIGURATION_CONSUMER =
          builder -> builder.addExecutionInterceptor(new TracingExecutionInterceptor());

  private static final ExecutionAttribute<Span> SPAN_ATTRIBUTE =
      new ExecutionAttribute<>("DatadogSpan");

  @Override
  public void beforeExecution(
      final Context.BeforeExecution context, final ExecutionAttributes executionAttributes) {
    final Span span = GlobalTracer.get().buildSpan("aws.command").start();
    AwsSdkClientDecorator.INSTANCE.afterStart(span);
    executionAttributes.putAttribute(SPAN_ATTRIBUTE, span);
  }

  @Override
  public void afterMarshalling(
      final Context.AfterMarshalling context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    final SdkHttpRequest httpRequest = context.httpRequest();

    AwsSdkClientDecorator.INSTANCE.onRequest(span, httpRequest);
    AwsSdkClientDecorator.INSTANCE.onAttributes(span, executionAttributes);
  }

  @Override
  public SdkHttpRequest modifyHttpRequest(
      final Context.ModifyHttpRequest context, final ExecutionAttributes executionAttributes) {
    final Tracer tracer = GlobalTracer.get();
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    final SdkHttpRequest.Builder builder = context.httpRequest().toBuilder();
    tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new InjectAdapter(builder));
    return builder.build();
  }

  @Override
  public void beforeTransmission(
      final Context.BeforeTransmission context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);

    // This scope will be closed by AwsHttpClientInstrumentation since ExecutionInterceptor API
    // doesn't provide a way to run code in the same thread after transmission has been scheduled.
    final Scope scope = GlobalTracer.get().scopeManager().activate(span, false);
    ((TraceScope) scope).setAsyncPropagation(true);
  }

  @Override
  public void afterExecution(
      final Context.AfterExecution context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    // Call onResponse on both types of responses:
    AwsSdkClientDecorator.INSTANCE.onResponse(span, context.response());
    AwsSdkClientDecorator.INSTANCE.onResponse(span, context.httpResponse());
    AwsSdkClientDecorator.INSTANCE.beforeFinish(span);
    span.finish();
  }

  @Override
  public void onExecutionFailure(
      final Context.FailedExecution context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    AwsSdkClientDecorator.INSTANCE.onError(span, context.exception());
  }

  public static Consumer<ClientOverrideConfiguration.Builder> getOverrideConfigurationConsumer() {
    return OVERRIDE_CONFIGURATION_CONSUMER;
  }

  /**
   * Inject headers into the request builder.
   *
   * <p>Note: we inject headers at aws-client level because aws requests may be signed and adding
   * headers on http-client level may break signature.
   */
  public static class InjectAdapter implements TextMap {

    private final SdkHttpRequest.Builder builder;

    public InjectAdapter(final SdkHttpRequest.Builder builder) {
      this.builder = builder;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      throw new UnsupportedOperationException(
          "This class should be used only with Tracer.extract()!");
    }

    @Override
    public void put(final String key, final String value) {
      builder.putHeader(key, value);
    }
  }
}

/*
 * Copyright 2017-2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package datadog.trace.instrumentation.aws.v2;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpRequest;

/** AWS request execution interceptor */
public class TracingExecutionInterceptor implements ExecutionInterceptor {

  private static final TracingExecutionInterceptor INSTANCE = new TracingExecutionInterceptor();
  // Note: it looks like this lambda doesn't get generated as a separate class file so we do not
  // need to inject helper for it.
  private static final Consumer<ClientOverrideConfiguration.Builder>
      OVERRIDE_CONFIGURATION_CONSUMER = builder -> builder.addExecutionInterceptor(INSTANCE);

  static final String COMPONENT_NAME = "java-aws-sdk";

  private static final ExecutionAttribute<Span> SPAN_ATTRIBUTE =
      new ExecutionAttribute<>("DatadogSpan");

  @Override
  public void beforeExecution(
      final Context.BeforeExecution context, final ExecutionAttributes executionAttributes) {
    final Tracer tracer = GlobalTracer.get();

    final Tracer.SpanBuilder builder =
        tracer
            .buildSpan("aws.command")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
            .withTag(DDTags.SERVICE_NAME, COMPONENT_NAME)
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT);
    executionAttributes.putAttribute(SPAN_ATTRIBUTE, builder.start());
  }

  @Override
  public void afterMarshalling(
      final Context.AfterMarshalling context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    final SdkHttpRequest httpRequest = context.httpRequest();

    Tags.HTTP_METHOD.set(span, httpRequest.method().name());

    try {
      final URI requestUri = httpRequest.getUri();
      final String uri =
          new URI(
                  requestUri.getScheme(),
                  null,
                  requestUri.getHost(),
                  requestUri.getPort(),
                  requestUri.getPath(),
                  null,
                  null)
              .toString();
      Tags.HTTP_URL.set(span, uri);
    } catch (final URISyntaxException e) {
      Tags.HTTP_URL.set(span, "failed-to-parse");
    }
    final String awsServiceName =
        executionAttributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    final String awsOperation =
        executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    // Resource Name has to be set after the HTTP_URL because otherwise decorators overwrite it
    span.setTag(DDTags.RESOURCE_NAME, awsServiceName + "." + awsOperation);

    span.setTag("aws.agent", COMPONENT_NAME);
    span.setTag("aws.service", awsServiceName);
    span.setTag("aws.operation", awsOperation);
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
    try {
      Tags.HTTP_STATUS.set(span, context.httpResponse().statusCode());
      final SdkResponse response = context.response();
      if (response instanceof AwsResponse) {
        span.setTag("aws.requestId", ((AwsResponse) response).responseMetadata().requestId());
      }
    } finally {
      span.finish();
    }
  }

  @Override
  public void onExecutionFailure(
      final Context.FailedExecution context, final ExecutionAttributes executionAttributes) {
    final Span span = executionAttributes.getAttribute(SPAN_ATTRIBUTE);
    Tags.ERROR.set(span, Boolean.TRUE);
    span.log(Collections.singletonMap(ERROR_OBJECT, context.exception()));
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

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

import static io.opentelemetry.auto.bootstrap.WeakMap.Provider.newWeakMap;

import io.opentelemetry.auto.bootstrap.WeakMap;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.ClientDecorator;
import io.opentelemetry.auto.instrumentation.awssdk.v2_2.shaded.AwsSdk;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.Context.AfterExecution;
import software.amazon.awssdk.core.interceptor.Context.AfterMarshalling;
import software.amazon.awssdk.core.interceptor.Context.AfterTransmission;
import software.amazon.awssdk.core.interceptor.Context.AfterUnmarshalling;
import software.amazon.awssdk.core.interceptor.Context.BeforeExecution;
import software.amazon.awssdk.core.interceptor.Context.BeforeMarshalling;
import software.amazon.awssdk.core.interceptor.Context.BeforeTransmission;
import software.amazon.awssdk.core.interceptor.Context.BeforeUnmarshalling;
import software.amazon.awssdk.core.interceptor.Context.FailedExecution;
import software.amazon.awssdk.core.interceptor.Context.ModifyHttpRequest;
import software.amazon.awssdk.core.interceptor.Context.ModifyHttpResponse;
import software.amazon.awssdk.core.interceptor.Context.ModifyRequest;
import software.amazon.awssdk.core.interceptor.Context.ModifyResponse;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

/**
 * {@link ExecutionInterceptor} that delegates to {@link AwsSdk}, augmenting {@link
 * #beforeTransmission(BeforeTransmission, ExecutionAttributes)} to make sure the span is set to the
 * current context to allow downstream instrumentation like Netty to pick it up.
 */
public class TracingExecutionInterceptor implements ExecutionInterceptor {

  // Keeps track of SDK clients that have been overridden by the user and don't need to be
  // overridden by us.
  public static final WeakMap<SdkClientBuilder, Boolean> OVERRIDDEN = newWeakMap();

  public static class ScopeHolder {
    public static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
  }

  // Note: it looks like this lambda doesn't get generated as a separate class file so we do not
  // need to inject helper for it.
  private static final Consumer<ClientOverrideConfiguration.Builder>
      OVERRIDE_CONFIGURATION_CONSUMER =
          builder ->
              builder.addExecutionInterceptor(
                  new TracingExecutionInterceptor(AwsSdk.newInterceptor()));

  private final ExecutionInterceptor delegate;

  private TracingExecutionInterceptor(ExecutionInterceptor delegate) {
    this.delegate = delegate;
  }

  /**
   * We keep this method here because it references Java8 classes and we would like to avoid
   * compiling this for instrumentation code that should load into Java7.
   */
  public static void overrideConfiguration(final SdkClientBuilder client) {
    // We intercept calls to overrideConfiguration to make sure when a user overrides the
    // configuration, we join their configuration. This means all we need to do is call the method
    // here and we will intercept the builder and add our interceptor.
    client.overrideConfiguration(builder -> {});
  }

  /**
   * We keep this method here because it references Java8 classes and we would like to avoid
   * compiling this for instrumentation code that should load into Java7.
   */
  public static void overrideConfiguration(final ClientOverrideConfiguration.Builder builder) {
    OVERRIDE_CONFIGURATION_CONSUMER.accept(builder);
  }

  public static void muzzleCheck() {
    // Noop
  }

  @Override
  public void beforeExecution(BeforeExecution context, ExecutionAttributes executionAttributes) {
    delegate.beforeExecution(context, executionAttributes);
  }

  @Override
  public SdkRequest modifyRequest(ModifyRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyRequest(context, executionAttributes);
  }

  @Override
  public void beforeMarshalling(
      BeforeMarshalling context, ExecutionAttributes executionAttributes) {
    delegate.beforeMarshalling(context, executionAttributes);
  }

  @Override
  public void afterMarshalling(AfterMarshalling context, ExecutionAttributes executionAttributes) {
    delegate.afterMarshalling(context, executionAttributes);
  }

  @Override
  public SdkHttpRequest modifyHttpRequest(
      ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpRequest(context, executionAttributes);
  }

  @Override
  public Optional<RequestBody> modifyHttpContent(
      ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpContent(context, executionAttributes);
  }

  @Override
  public Optional<AsyncRequestBody> modifyAsyncHttpContent(
      ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyAsyncHttpContent(context, executionAttributes);
  }

  @Override
  public void beforeTransmission(
      BeforeTransmission context, ExecutionAttributes executionAttributes) {
    delegate.beforeTransmission(context, executionAttributes);
    Span span = AwsSdk.getSpanFromAttributes(executionAttributes);
    if (span != null) {
      // This scope will be closed by AwsHttpClientInstrumentation since ExecutionInterceptor API
      // doesn't provide a way to run code in the same thread after transmission has been scheduled.
      ScopeHolder.CURRENT.set(
          ContextUtils.withScopedContext(ClientDecorator.currentContextWith(span)));
    }
  }

  @Override
  public void afterTransmission(
      AfterTransmission context, ExecutionAttributes executionAttributes) {
    delegate.afterTransmission(context, executionAttributes);
  }

  @Override
  public SdkHttpResponse modifyHttpResponse(
      ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpResponse(context, executionAttributes);
  }

  @Override
  public Optional<Publisher<ByteBuffer>> modifyAsyncHttpResponseContent(
      ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyAsyncHttpResponseContent(context, executionAttributes);
  }

  @Override
  public Optional<InputStream> modifyHttpResponseContent(
      ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpResponseContent(context, executionAttributes);
  }

  @Override
  public void beforeUnmarshalling(
      BeforeUnmarshalling context, ExecutionAttributes executionAttributes) {
    delegate.beforeUnmarshalling(context, executionAttributes);
  }

  @Override
  public void afterUnmarshalling(
      AfterUnmarshalling context, ExecutionAttributes executionAttributes) {
    delegate.afterUnmarshalling(context, executionAttributes);
  }

  @Override
  public SdkResponse modifyResponse(
      ModifyResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyResponse(context, executionAttributes);
  }

  @Override
  public void afterExecution(AfterExecution context, ExecutionAttributes executionAttributes) {
    delegate.afterExecution(context, executionAttributes);
  }

  @Override
  public Throwable modifyException(
      FailedExecution context, ExecutionAttributes executionAttributes) {
    return delegate.modifyException(context, executionAttributes);
  }

  @Override
  public void onExecutionFailure(FailedExecution context, ExecutionAttributes executionAttributes) {
    delegate.onExecutionFailure(context, executionAttributes);
  }
}

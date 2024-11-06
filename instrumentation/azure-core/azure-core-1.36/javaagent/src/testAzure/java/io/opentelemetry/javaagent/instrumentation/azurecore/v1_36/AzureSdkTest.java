/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurecore.v1_36;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.annotation.ExpectedResponses;
import com.azure.core.annotation.Get;
import com.azure.core.annotation.Host;
import com.azure.core.annotation.ServiceInterface;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.core.http.policy.HttpPolicyProviders;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.RestProxy;
import com.azure.core.test.http.MockHttpResponse;
import com.azure.core.util.ClientOptions;
import com.azure.core.util.Context;
import com.azure.core.util.TracingOptions;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AzureSdkTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testHelperClassesInjected() {
    com.azure.core.util.tracing.Tracer azTracer = createAzTracer();
    assertThat(azTracer.isEnabled()).isTrue();

    assertThat(azTracer.getClass().getName())
        .isEqualTo(
            "io.opentelemetry.javaagent.instrumentation.azurecore.v1_36.shaded"
                + ".com.azure.core.tracing.opentelemetry.OpenTelemetryTracer");
  }

  @Test
  void testSpan() {
    com.azure.core.util.tracing.Tracer azTracer = createAzTracer();
    Context context = azTracer.start("hello", Context.NONE);
    azTracer.end(null, null, context);

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("hello")
                        .hasKind(SpanKind.INTERNAL)
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(AttributeKey.stringKey("az.namespace"), "otel.tests"))));
  }

  @Test
  void testPipelineAndSuppression() {
    AtomicBoolean hasClientAndHttpKeys = new AtomicBoolean(false);

    HttpClient mockClient =
        request ->
            Mono.defer(
                () -> {
                  // check if suppression is working
                  hasClientAndHttpKeys.set(hasClientAndHttpSpans());
                  return Mono.just(new MockHttpResponse(request, 200));
                });

    StepVerifier.create(createService(mockClient, true).testMethod())
        .expectNextCount(1)
        .expectComplete()
        .verify();

    assertThat(hasClientAndHttpKeys.get()).isTrue();
    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("myService.testMethod")
                        .hasKind(SpanKind.INTERNAL)
                        .hasStatus(StatusData.unset())
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasName(Boolean.getBoolean("testLatestDeps") ? "GET" : "HTTP GET")
                        .hasStatus(StatusData.unset())
                        .hasAttribute(HTTP_RESPONSE_STATUS_CODE, 200L)));
  }

  @Test
  void testDisabledTracingNoSuppression() {
    AtomicBoolean hasClientAndHttpKeys = new AtomicBoolean(false);

    HttpClient mockClient =
        request ->
            Mono.defer(
                () -> {
                  // check no suppression
                  hasClientAndHttpKeys.set(hasClientAndHttpSpans());
                  return Mono.just(new MockHttpResponse(request, 200));
                });

    StepVerifier.create(createService(mockClient, false).testMethod())
        .expectNextCount(1)
        .expectComplete()
        .verify();

    assertThat(hasClientAndHttpKeys.get()).isFalse();
  }

  private static com.azure.core.util.tracing.Tracer createAzTracer() {
    com.azure.core.util.tracing.TracerProvider azProvider =
        com.azure.core.util.tracing.TracerProvider.getDefaultProvider();
    return azProvider.createTracer("test-lib", "test-version", "otel.tests", null);
  }

  private static TestInterface createService(HttpClient httpClient, boolean tracingEnabled) {
    List<HttpPipelinePolicy> policies = new ArrayList<>();
    HttpPolicyProviders.addAfterRetryPolicies(policies);

    ClientOptions clientOptions =
        new ClientOptions().setTracingOptions(new TracingOptions().setEnabled(tracingEnabled));
    HttpPipeline pipeline =
        new HttpPipelineBuilder()
            .policies(policies.toArray(new HttpPipelinePolicy[0]))
            .httpClient(httpClient)
            .clientOptions(clientOptions)
            .build();

    return RestProxy.create(TestInterface.class, pipeline);
  }

  private static boolean hasClientAndHttpSpans() {
    io.opentelemetry.context.Context ctx = io.opentelemetry.context.Context.current();
    return SpanKey.KIND_CLIENT.fromContextOrNull(ctx) != null
        && SpanKey.HTTP_CLIENT.fromContextOrNull(ctx) != null;
  }

  @Host("https://azure.com")
  @ServiceInterface(name = "myService")
  interface TestInterface {
    @Get("path")
    @ExpectedResponses({200})
    Mono<Response<Void>> testMethod();
  }
}

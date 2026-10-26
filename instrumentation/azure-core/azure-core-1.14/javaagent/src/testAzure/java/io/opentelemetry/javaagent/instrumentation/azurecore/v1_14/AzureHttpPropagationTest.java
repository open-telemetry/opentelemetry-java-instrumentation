/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurecore.v1_14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.core.http.policy.HttpPolicyProviders;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

class AzureHttpPropagationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void propagatesHttpSpan(boolean withParent) {
    boolean sampled = !"always_off".equals(System.getProperty("otel.traces.sampler"));
    Span parent =
        withParent
            ? GlobalOpenTelemetry.getTracer("test").spanBuilder("parent").setNoParent().startSpan()
            : Span.getInvalid();
    AtomicReference<String> traceparent = new AtomicReference<>();
    HttpClient transport =
        request -> {
          traceparent.set(request.getHeaders().getValue("traceparent"));
          HttpResponse response = mock(HttpResponse.class);
          when(response.getStatusCode()).thenReturn(200);
          return Mono.defer(
              () -> {
                assertThat(SpanKey.HTTP_CLIENT.fromContextOrNull(Context.current())).isNotNull();
                return Mono.just(response);
              });
        };
    List<HttpPipelinePolicy> policies = new ArrayList<>();
    HttpPolicyProviders.addAfterRetryPolicies(policies);
    HttpPipeline pipeline =
        new HttpPipelineBuilder()
            .policies(policies.toArray(new HttpPipelinePolicy[0]))
            .httpClient(transport)
            .build();

    try (Scope ignored = Context.root().with(parent).makeCurrent()) {
      pipeline.send(new HttpRequest(HttpMethod.GET, "https://azure.com/path")).block();
    } finally {
      parent.end();
    }

    assertThat(traceparent.get()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    String[] fields = traceparent.get().split("-");
    SpanContext propagated =
        SpanContext.create(
            fields[1], fields[2], TraceFlags.fromHex(fields[3], 0), TraceState.getDefault());
    assertThat(propagated.isValid()).isTrue();
    assertThat(propagated.isSampled()).isEqualTo(sampled);
    if (withParent) {
      assertThat(propagated.getTraceId()).isEqualTo(parent.getSpanContext().getTraceId());
      assertThat(propagated.getSpanId()).isNotEqualTo(parent.getSpanContext().getSpanId());
    }

    if (!sampled) {
      // The pipeline has completed and the policy has ended its dropped span synchronously.
      assertThat(testing.spans()).isEmpty();
    } else if (withParent) {
      testing.waitAndAssertTracesWithoutScopeVersionVerification(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasNoParent(),
                  span ->
                      span.hasName("/path")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasTraceId(propagated.getTraceId())
                          .hasSpanId(propagated.getSpanId())));
    } else {
      testing.waitAndAssertTracesWithoutScopeVersionVerification(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("/path")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasTraceId(propagated.getTraceId())
                          .hasSpanId(propagated.getSpanId())));
    }
  }
}

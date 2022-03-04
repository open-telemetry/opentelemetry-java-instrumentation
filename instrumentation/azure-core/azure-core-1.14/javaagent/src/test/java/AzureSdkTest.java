/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.core.http.policy.HttpPolicyProviders;
import com.azure.core.util.Context;
import com.azure.core.util.tracing.TracerProxy;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AzureSdkTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testHelperClassesInjected() {

    assertThat(TracerProxy.isTracingEnabled()).isTrue();

    List<HttpPipelinePolicy> list = new ArrayList<>();
    HttpPolicyProviders.addAfterRetryPolicies(list);

    assertThat(list).hasSize(1);
    assertThat(list.get(0).getClass().getName())
        .isEqualTo(
            "io.opentelemetry.javaagent.instrumentation.azurecore.v1_14.shaded"
                + ".com.azure.core.tracing.opentelemetry.OpenTelemetryHttpPolicy");
  }

  @Test
  void testSpan() {
    Context context = TracerProxy.start("hello", Context.NONE);
    TracerProxy.end(200, null, context);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("hello")
                        .hasKind(SpanKind.INTERNAL)
                        .hasStatus(StatusData.ok())
                        .hasAttributesSatisfying(Attributes::isEmpty)));
  }
}

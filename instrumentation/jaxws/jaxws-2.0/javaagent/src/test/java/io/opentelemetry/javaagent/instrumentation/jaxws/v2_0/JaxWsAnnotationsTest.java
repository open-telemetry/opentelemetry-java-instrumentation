/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JaxWsAnnotationsTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void webServiceProviderSpan() {
    new SoapProvider().invoke(null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SoapProvider.invoke")
                        .hasNoParent()
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME, SoapProvider.class.getName() + ".invoke"))));
  }
}

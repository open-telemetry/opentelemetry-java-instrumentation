/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v3_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JaxWsAnnotationsTest extends AgentInstrumentationSpecification {

  @RegisterExtension
  protected static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Test
  void webServiceProvidersGenerateSpans() {

    SoapProvider soapProvider = new SoapProvider();
    soapProvider.invoke(null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SoapProvider.invoke")
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                "io.opentelemetry.javaagent.instrumentation.jaxws.v3_0.SoapProvider"),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "invoke"))));
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import java.util.Collections;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.component.aws.sqs.SqsComponent;
import org.apache.camel.component.aws.sqs.SqsConfiguration;
import org.apache.camel.component.aws.sqs.SqsEndpoint;
import org.junit.jupiter.api.Test;

public class CamelPropagationUtilTest {

  @Test
  public void shouldExtractAwsParent() {

    // given
    Endpoint endpoint = new SqsEndpoint("", new SqsComponent(), new SqsConfiguration());
    Map<String, Object> exchangeHeaders =
        Collections.singletonMap(
            "AWSTraceHeader",
            "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1\n");

    // when
    Context parent = CamelPropagationUtil.extractParent(exchangeHeaders, endpoint);

    // then
    Span parentSpan = Span.fromContext(parent);
    SpanContext parentSpanContext = parentSpan.getSpanContext();
    assertThat(parentSpanContext.getTraceId()).isEqualTo("5759e988bd862e3fe1be46a994272793");
    assertThat(parentSpanContext.getSpanId()).isEqualTo("53995c3f42cd8ad8");
  }
}

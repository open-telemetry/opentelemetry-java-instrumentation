/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import java.net.URI;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.component.aws.sqs.SqsComponent;
import org.apache.camel.component.aws.sqs.SqsConfiguration;
import org.apache.camel.component.aws.sqs.SqsEndpoint;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpEndpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CamelPropagationUtilTest {

  @BeforeAll
  static void setUp() {
    GlobalOpenTelemetry.set(
        OpenTelemetry.propagating(
            ContextPropagators.create(W3CTraceContextPropagator.getInstance())));
  }

  @Test
  void shouldExtractHttpParentForHttpEndpoint() throws Exception {
    // given
    Endpoint endpoint = new HttpEndpoint("", new HttpComponent(), URI.create(""));
    Map<String, Object> exchangeHeaders =
        singletonMap("traceparent", "00-1f7f8dab3f0043b1b9cf0a75caf57510-a13825abcb764bd3-01");

    // when
    Context parent = CamelPropagationUtil.extractParent(exchangeHeaders, endpoint);

    // then
    Span parentSpan = Span.fromContext(parent);
    SpanContext parentSpanContext = parentSpan.getSpanContext();
    assertThat(parentSpanContext.getTraceId()).isEqualTo("1f7f8dab3f0043b1b9cf0a75caf57510");
    assertThat(parentSpanContext.getSpanId()).isEqualTo("a13825abcb764bd3");
  }

  @Test
  void shouldNotFailExtractingNullHttpParentForHttpEndpoint() throws Exception {
    // given
    Endpoint endpoint = new HttpEndpoint("", new HttpComponent(), URI.create(""));
    Map<String, Object> exchangeHeaders = singletonMap("traceparent", null);

    // when
    Context parent = CamelPropagationUtil.extractParent(exchangeHeaders, endpoint);

    // then
    Span parentSpan = Span.fromContext(parent);
    SpanContext parentSpanContext = parentSpan.getSpanContext();
    assertThat(parentSpanContext.isValid()).isEqualTo(false);
  }

  @Test
  void shouldNotFailExtractingNullAwsParentForSqsEndpoint() {
    // given
    Endpoint endpoint = new SqsEndpoint("", new SqsComponent(), new SqsConfiguration());
    Map<String, Object> exchangeHeaders = singletonMap("AWSTraceHeader", null);

    // when
    Context parent = CamelPropagationUtil.extractParent(exchangeHeaders, endpoint);

    // then
    Span parentSpan = Span.fromContext(parent);
    SpanContext parentSpanContext = parentSpan.getSpanContext();
    assertThat(parentSpanContext.isValid()).isEqualTo(false);
  }

  @Test
  void shouldExtractAwsParentForSqsEndpoint() {
    // given
    Endpoint endpoint = new SqsEndpoint("", new SqsComponent(), new SqsConfiguration());
    Map<String, Object> exchangeHeaders =
        singletonMap(
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

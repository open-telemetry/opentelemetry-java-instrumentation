/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.javaagent.instrumentation.camel.v2_20.decorators.DecoratorRegistry;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.jupiter.api.Test;

class CamelMessagingAttributesGetterTest {

  private final CamelMessagingAttributesGetter getter = new CamelMessagingAttributesGetter();

  @Test
  void headers() {
    CamelRequest request = request();
    Map<String, Object> headers = new HashMap<>();
    headers.put("Test-Header", "value");
    headers.put("Number", 42);
    headers.put("Null", null);
    request.getExchange().getIn().setHeaders(headers);

    assertThat(getter.getMessageHeaderNames(request))
        .containsExactlyInAnyOrder("Test-Header", "Number", "Null");
    assertThat(getter.getMessageHeader(request, "Test-Header")).containsExactly("value");
    assertThat(getter.getMessageHeader(request, "Number")).containsExactly("42");
    assertThat(getter.getMessageHeader(request, "Null")).isEmpty();
    assertThat(getter.getMessageHeader(request, "missing")).isEmpty();

    Iterable<String> names = getter.getMessageHeaderNames(request);
    request.getExchange().getIn().getHeaders().clear();
    assertThat(names).containsExactlyInAnyOrder("Test-Header", "Number", "Null");
    assertThat(getter.getMessageHeaderNames(request)).isEmpty();
  }

  @Test
  void errorType() {
    CamelRequest request = request();
    IllegalStateException error = new IllegalStateException();
    request.getExchange().setException(error);

    assertThat(getter.getErrorType(request, null, error))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(getter.getErrorType(request, null, null)).isEqualTo("_OTHER");
  }

  @Test
  void selectors() {
    CamelRequest request = request();
    request.getExchange().getIn().setHeader("Test-Header", "value");
    request.getExchange().getIn().setHeader("Authorization", "private");

    IncludeExclude wildcard = IncludeExclude.builder().setIncluded(singletonList("Test-*")).build();
    IncludeExclude excludeOnly =
        IncludeExclude.builder().setExcluded(singletonList("Authorization")).build();
    for (IncludeExclude selector : new IncludeExclude[] {wildcard, excludeOnly}) {
      AttributesBuilder attributes = Attributes.builder();
      MessagingAttributesExtractor.builder(getter, MessagingOperationType.PROCESS, "process")
          .setHeaders(selector)
          .build()
          .onEnd(attributes, Context.root(), request, null, null);

      assertThat(attributes.build().asMap())
          .containsOnly(
              entry(stringArrayKey("messaging.header.Test-Header"), singletonList("value")));
    }
  }

  private static CamelRequest request() {
    Endpoint endpoint = mock(Endpoint.class);
    when(endpoint.getEndpointUri()).thenReturn("jms:queue:test");
    Exchange exchange = new DefaultExchange(new DefaultCamelContext());
    return CamelRequest.create(
        new DecoratorRegistry().forComponent("jms"),
        exchange,
        endpoint,
        CamelDirection.INBOUND,
        SpanKind.CONSUMER);
  }
}

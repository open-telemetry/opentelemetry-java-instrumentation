/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.junit.jupiter.api.Test;

class TracingStartInInterceptorTest {

  @Test
  void shouldUseFallbackSpanNameWhenBindingOperationInfoIsNull() {
    // given Exchange without BindingOperationInfo.class
    Message message = new MessageImpl();
    message.setExchange(new ExchangeImpl());

    // when creating a CxfRequest
    CxfRequest request = new CxfRequest(message);

    // then span name falls back to "jaxws" instead of throwing NPE
    assertThat(request.spanName()).isEqualTo("jaxws");
  }
}

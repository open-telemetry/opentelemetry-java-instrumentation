/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.junit.jupiter.api.Test;

class TracingStartInInterceptorTest {

  @Test
  void shouldNotThrowExceptionIfSpanNameIsNull() {
    // given Exchange without BindingOperationInfo.class -> spanName eq null
    Message message = new MessageImpl();
    message.setExchange(new ExchangeImpl());

    // when interceptor handling message
    TracingStartInInterceptor tracingStartInInterceptor = new TracingStartInInterceptor();
    tracingStartInInterceptor.handleMessage(message);

    // then no NPE
  }
}

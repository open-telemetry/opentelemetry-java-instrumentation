/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.MessageAttribute;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqsMessageAttributesGetterTest {

  private final SqsMessageAttributesGetter getter = new SqsMessageAttributesGetter();

  @Test
  void headers() {
    SQSMessage message = SqsEventTestUtils.newMessage();
    MessageAttribute attribute = new MessageAttribute();
    attribute.setStringValue("value");
    MessageAttribute binary = new MessageAttribute();
    binary.setBinaryValue(ByteBuffer.wrap(new byte[] {1}));
    Map<String, MessageAttribute> attributes = new HashMap<>();
    attributes.put("Test-Header", attribute);
    attributes.put("Binary-Header", binary);
    attributes.put("Null-Header", null);
    message.setMessageAttributes(attributes);

    assertThat(getter.getMessageHeader(message, "Test-Header")).containsExactly("value");
    assertThat(getter.getMessageHeader(message, "test-header")).isEmpty();
    assertThat(getter.getMessageHeader(message, "Binary-Header")).isEmpty();
    assertThat(getter.getMessageHeader(message, "Null-Header")).isEmpty();
    assertThat(getter.getMessageHeader(message, "missing")).isEmpty();
  }

  @Test
  void noHeaders() {
    SQSMessage message = SqsEventTestUtils.newMessage();
    assertThat(getter.getMessageHeader(message, "missing")).isEmpty();
  }
}

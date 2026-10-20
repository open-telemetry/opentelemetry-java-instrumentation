/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.MessageAttribute;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqsMessageAttributesGetterTest {

  private final SqsMessageAttributesGetter getter = new SqsMessageAttributesGetter();

  @Test
  void headers() {
    SQSMessage message = new SQSMessage();
    MessageAttribute attribute = new MessageAttribute();
    attribute.setStringValue("value");
    Map<String, MessageAttribute> attributes = new HashMap<>();
    attributes.put("Test-Header", attribute);
    attributes.put("Binary-Header", new MessageAttribute());
    message.setMessageAttributes(attributes);

    assertThat(getter.getMessageHeaderNames(message))
        .containsExactlyInAnyOrder("Test-Header", "Binary-Header");
    assertThat(getter.getMessageHeader(message, "Test-Header")).containsExactly("value");
    assertThat(getter.getMessageHeader(message, "test-header")).isEmpty();
    assertThat(getter.getMessageHeader(message, "Binary-Header")).isEmpty();
    assertThat(getter.getMessageHeader(message, "missing")).isEmpty();

    Iterable<String> names = getter.getMessageHeaderNames(message);
    attributes.clear();
    assertThat(names).containsExactlyInAnyOrder("Test-Header", "Binary-Header");
  }

  @Test
  void noHeaders() {
    SQSMessage message = new SQSMessage();
    assertThat(getter.getMessageHeaderNames(message)).isEmpty();
    assertThat(getter.getMessageHeader(message, "missing")).isEmpty();
    assertThat(new SqsEventAttributesGetter().getMessageHeaderNames(new SQSEvent())).isEmpty();
  }
}

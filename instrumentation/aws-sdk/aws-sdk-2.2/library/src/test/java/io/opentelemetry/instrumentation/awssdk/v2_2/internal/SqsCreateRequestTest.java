/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqsCreateRequestTest {

  @Test
  void handlesMissingQueueUrl() {
    assertThat(new SqsCreateRequest(null, emptyMap()).getDestination()).isNull();
  }

  @Test
  void exposesMessageAttributeNames() {
    SqsCreateRequest request =
        new SqsCreateRequest("https://example.com/queue", singletonMap("header", "value"));

    SqsCreateRequestAttributesGetter getter = new SqsCreateRequestAttributesGetter();
    assertThat(getter.getMessageHeaderNames(request)).containsExactly("header");
    assertThat(getter.getMessageHeader(request, "header")).containsExactly("value");
  }
}

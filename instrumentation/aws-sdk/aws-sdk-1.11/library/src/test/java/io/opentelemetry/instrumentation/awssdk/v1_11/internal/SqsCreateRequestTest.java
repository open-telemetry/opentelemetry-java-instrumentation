/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqsCreateRequestTest {

  @Test
  void handlesMissingQueueUrl() {
    assertThat(new SqsCreateRequest(null, emptyMap()).getDestination()).isNull();
  }
}

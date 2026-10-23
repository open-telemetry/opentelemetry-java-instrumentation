/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.internal.SpanKey;
import org.junit.jupiter.api.Test;

class AwsSdkHttpClientSuppressionAttributesExtractorTest {

  @Test
  void suppressesHttpClientSpans() {
    assertThat(new AwsSdkHttpClientSuppressionAttributesExtractor().internalGetSpanKey())
        .isEqualTo(SpanKey.HTTP_CLIENT);
  }
}

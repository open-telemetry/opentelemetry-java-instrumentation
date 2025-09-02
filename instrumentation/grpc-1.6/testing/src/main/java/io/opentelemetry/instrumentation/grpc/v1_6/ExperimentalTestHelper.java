/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.function.Consumer;

class ExperimentalTestHelper {
  private static final boolean isEnabled =
      Boolean.getBoolean("otel.instrumentation.grpc.experimental-span-attributes");

  static final AttributeKey<Long> GRPC_RECEIVED_MESSAGE_COUNT =
      longKey("grpc.received.message_count");
  static final AttributeKey<Long> GRPC_SENT_MESSAGE_COUNT = longKey("grpc.sent.message_count");

  public static AttributeAssertion experimentalSatisfies(
      AttributeKey<Long> key, Consumer<? super Long> assertion) {
    return satisfies(
        key, val -> val.satisfiesAnyOf(v -> assertThat(isEnabled).isFalse(), assertion::accept));
  }

  private ExperimentalTestHelper() {}
}

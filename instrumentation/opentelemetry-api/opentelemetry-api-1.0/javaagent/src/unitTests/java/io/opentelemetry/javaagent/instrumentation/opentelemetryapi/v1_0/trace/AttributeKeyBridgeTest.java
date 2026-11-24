/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.trace;

import static application.io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import application.io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import org.junit.jupiter.api.Test;

class AttributeKeyBridgeTest {
  @Test
  void reusesConvertedKey() {
    AttributeKey<String> text = stringKey("order.id");

    io.opentelemetry.api.common.AttributeKey<?> bridged = Bridging.toAgent(text);
    assertThat(bridged.getKey()).isEqualTo("order.id");
    assertThat(bridged.getType()).isEqualTo(AttributeType.STRING);
    assertThat(Bridging.toAgent(text)).isSameAs(bridged);
  }
}

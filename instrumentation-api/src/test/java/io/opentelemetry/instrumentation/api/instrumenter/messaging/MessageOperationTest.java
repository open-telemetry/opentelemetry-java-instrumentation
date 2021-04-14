/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MessageOperationTest {
  @ParameterizedTest
  @EnumSource(MessageOperation.class)
  void shouldGetCorrectOperationName(MessageOperation operation) {
    assertEquals(operation.name().toLowerCase(), operation.operationName());
  }
}

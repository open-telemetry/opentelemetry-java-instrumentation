/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import javax.annotation.Nullable;

class ExperimentalTestHelper {
  private static final boolean isEnabled =
      Boolean.getBoolean("otel.instrumentation.hystrix.experimental-span-attributes");

  static final AttributeKey<String> HYSTRIX_COMMAND = stringKey("hystrix.command");
  static final AttributeKey<String> HYSTRIX_GROUP = stringKey("hystrix.group");
  static final AttributeKey<Boolean> HYSTRIX_CIRCUIT_OPEN = booleanKey("hystrix.circuit_open");

  @Nullable
  static <T> T experimental(T value) {
    if (isEnabled) {
      return value;
    }
    return null;
  }

  private ExperimentalTestHelper() {}
}

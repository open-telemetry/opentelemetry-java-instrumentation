/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.function.Consumer;
import javax.annotation.Nullable;

public class ExperimentalTestHelper {
  public static final boolean isEnabled =
      Boolean.getBoolean("otel.instrumentation.grpc.experimental-span-attributes");

  @Nullable
  public static String experimental(String value) {
    if (isEnabled) {
      return null;
    }
    return value;
  }

  public static AttributeAssertion experimentalSatisfies(
      AttributeKey<String> key, Consumer<? super String> assertion) {
    return satisfies(
        key,
        val -> {
          if (isEnabled) {
            val.satisfies(assertion::accept);
          }
        });
  }

  private ExperimentalTestHelper() {}
}

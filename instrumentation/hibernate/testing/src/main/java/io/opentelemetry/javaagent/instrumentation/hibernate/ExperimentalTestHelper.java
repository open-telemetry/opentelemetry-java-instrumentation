/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.function.Consumer;

public class ExperimentalTestHelper {
  public static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.hibernate.experimental-span-attributes");

  public static final AttributeKey<String> HIBERNATE_SESSION_ID = stringKey("hibernate.session_id");

  public static String experimental(String value) {
    if (EXPERIMENTAL_ATTRIBUTES) {
      return value;
    }
    return null;
  }

  public static AttributeAssertion experimentalSatisfies(
      AttributeKey<String> key, Consumer<? super String> assertion) {
    return satisfies(
        key,
        val -> {
          if (EXPERIMENTAL_ATTRIBUTES) {
            val.satisfies(assertion::accept);
          }
        });
  }

  private ExperimentalTestHelper() {}
}

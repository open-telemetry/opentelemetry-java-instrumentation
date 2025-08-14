/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;

public class ExperimentalTest {
  private static final String EXPERIMENTAL_FLAG =
      "otel.instrumentation.camel.experimental-span-attributes";

  public static String experimental(String value) {
    if (!Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
      return null;
    }
    return value;
  }

  static AttributeAssertion experimentalSatisfies(
      AttributeKey<String> key, OpenTelemetryAssertions.StringAssertConsumer assertion) {
    if (Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
      return OpenTelemetryAssertions.satisfies(key, assertion);
    } else {
      return OpenTelemetryAssertions.equalTo(key, null);
    }
  }

  private ExperimentalTest() {}
}

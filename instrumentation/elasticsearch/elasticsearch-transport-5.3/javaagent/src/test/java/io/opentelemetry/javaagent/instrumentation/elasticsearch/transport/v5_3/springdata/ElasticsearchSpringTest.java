/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.springdata;

abstract class ElasticsearchSpringTest {
  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.elasticsearch.experimental-span-attributes");

  protected static String experimental(String value) {
    if (!EXPERIMENTAL_ATTRIBUTES) {
      return null;
    }
    return value;
  }

  protected static Long experimental(long value) {
    if (!EXPERIMENTAL_ATTRIBUTES) {
      return null;
    }
    return value;
  }
}

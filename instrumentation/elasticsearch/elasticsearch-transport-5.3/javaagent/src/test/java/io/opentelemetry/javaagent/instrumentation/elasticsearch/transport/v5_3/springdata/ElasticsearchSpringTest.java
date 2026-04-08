/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.springdata;

abstract class ElasticsearchSpringTest {
  protected static final String EXPERIMENTAL_FLAG =
      "otel.instrumentation.elasticsearch.experimental-span-attributes";

  protected static String experimental(String value) {
    if (!Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
      return null;
    }
    return value;
  }

  protected static Long experimental(long value) {
    if (!Boolean.getBoolean(EXPERIMENTAL_FLAG)) {
      return null;
    }
    return value;
  }
}

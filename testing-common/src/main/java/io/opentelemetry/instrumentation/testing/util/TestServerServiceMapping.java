/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import javax.annotation.Nullable;

/** Expected values for the shared javaagent server-service mapping test fixture. */
public final class TestServerServiceMapping {

  private static final String SERVICE_MAPPING_PROPERTY =
      "otel.instrumentation.common.server-service-mapping";
  private static final String MAPPED_SERVICE_NAME = "test-server-service";

  @Nullable
  public static String expectedMappedServiceName() {
    return System.getProperty(SERVICE_MAPPING_PROPERTY) == null ? null : MAPPED_SERVICE_NAME;
  }

  private TestServerServiceMapping() {}
}

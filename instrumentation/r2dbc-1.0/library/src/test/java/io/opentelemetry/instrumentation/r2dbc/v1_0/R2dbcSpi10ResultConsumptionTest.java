/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

class R2dbcSpi10ResultConsumptionTest extends AbstractR2dbcLibraryResultConsumptionTest {

  @Override
  protected Class<?> expectedUpdateCountType() {
    return Long.class;
  }

  @Override
  protected Number expectedUpdateCount() {
    return 1L;
  }
}

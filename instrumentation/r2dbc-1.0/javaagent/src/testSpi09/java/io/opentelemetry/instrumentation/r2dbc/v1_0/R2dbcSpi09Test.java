/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.AbstractR2dbcAgentResultConsumptionTest;

class R2dbcSpi09Test extends AbstractR2dbcAgentResultConsumptionTest {

  @Override
  protected Class<?> expectedUpdateCountType() {
    return Integer.class;
  }

  @Override
  protected Number expectedUpdateCount() {
    return 1;
  }
}

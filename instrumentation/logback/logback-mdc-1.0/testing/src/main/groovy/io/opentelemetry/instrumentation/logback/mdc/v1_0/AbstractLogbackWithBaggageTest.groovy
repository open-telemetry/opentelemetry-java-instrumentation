/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.mdc.v1_0

abstract class AbstractLogbackWithBaggageTest extends AbstractLogbackTest {
  @Override
  boolean expectBaggage() {
    return true
  }
}

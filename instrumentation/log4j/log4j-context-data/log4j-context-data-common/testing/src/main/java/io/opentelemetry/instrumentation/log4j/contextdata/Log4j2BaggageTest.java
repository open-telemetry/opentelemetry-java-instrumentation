/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata;

public abstract class Log4j2BaggageTest extends Log4j2Test {
  @Override
  boolean expectBaggage() {
    return true;
  }
}

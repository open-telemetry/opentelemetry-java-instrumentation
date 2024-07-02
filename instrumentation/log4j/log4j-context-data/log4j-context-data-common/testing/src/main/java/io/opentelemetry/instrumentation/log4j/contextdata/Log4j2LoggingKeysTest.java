/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata;

public abstract class Log4j2LoggingKeysTest extends Log4j2Test {
  @Override
  boolean expectLoggingKeys() {
    return true;
  }
}

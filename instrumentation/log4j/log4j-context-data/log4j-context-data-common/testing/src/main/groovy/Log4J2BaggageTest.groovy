/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

abstract class Log4J2BaggageTest extends Log4j2Test {
  @Override
  boolean expectBaggage() {
    return true
  }
}

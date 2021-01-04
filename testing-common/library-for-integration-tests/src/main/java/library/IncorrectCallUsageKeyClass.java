/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package library;

public class IncorrectCallUsageKeyClass {
  public boolean isInstrumented() {
    return false;
  }

  public int incorrectCallUsage() {
    // instrumentation will not apply to this class because advice incorrectly uses context api
    return -1;
  }
}

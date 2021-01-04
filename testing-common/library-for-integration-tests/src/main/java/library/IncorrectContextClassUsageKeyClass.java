/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package library;

public class IncorrectContextClassUsageKeyClass {
  public boolean isInstrumented() {
    return false;
  }

  public int incorrectContextClassUsage() {
    // instrumentation will not apply to this class because advice incorrectly uses context api
    return -1;
  }
}

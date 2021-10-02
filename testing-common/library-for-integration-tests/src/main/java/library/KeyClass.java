/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package library;

public class KeyClass {
  public boolean isInstrumented() {
    // implementation replaced with test instrumentation
    return false;
  }

  public int incrementContextCount() {
    // implementation replaced with test instrumentation
    return -1;
  }

  public int getContextCount() {
    // implementation replaced with test instrumentation
    return -1;
  }

  public void putContextCount(int value) {
    // implementation replaced with test instrumentation
  }

  public void removeContextCount() {
    // implementation replaced with test instrumentation
  }
}

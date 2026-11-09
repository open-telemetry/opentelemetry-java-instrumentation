/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package library;

/** A class that is used that field injection can be disabled. */
public class DisabledKeyClass extends KeyClass {
  @Override
  public boolean isInstrumented() {
    return false;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package helper;

class DuplicateHelperTestClass {

  // this method is transformed by instrumentation
  public static String transform(String string) {
    return string;
  }

  private DuplicateHelperTestClass() {}
}

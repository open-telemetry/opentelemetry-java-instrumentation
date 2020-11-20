/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation;

public class TestHelperDepsEnum {
  public String get() {
    return EnumWithOverridingClasses.get("ONE");
  }

  public enum EnumWithOverridingClasses {
    ONE {
      public String something() {
        return "1";
      }
    },
    TWO {
      public String something() {
        return "42";
      }
    };

    abstract String something();

    static String get(String name) {
      for (EnumWithOverridingClasses val : values()) {
        if (val.name().equals(name)) {
          return val.something();
        }
      }
      return null;
    }
  }
}

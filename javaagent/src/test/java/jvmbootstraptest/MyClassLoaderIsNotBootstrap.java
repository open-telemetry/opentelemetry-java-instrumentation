/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

public class MyClassLoaderIsNotBootstrap {
  public static void main(String[] args) {
    if (MyClassLoaderIsNotBootstrap.class.getClassLoader() == null) {
      throw new RuntimeException("Application level class was loaded by bootstrap classloader");
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

import java.net.URL;
import java.net.URLClassLoader;

public class AgentLoadedChecker {
  public static void main(String[] args) throws ClassNotFoundException {
    // Empty class loader that delegates to bootstrap
    URLClassLoader emptyClassLoader = new URLClassLoader(new URL[] {}, null);
    Class<?> agentClass =
        emptyClassLoader.loadClass("io.opentelemetry.javaagent.bootstrap.AgentInitializer");

    if (agentClass.getClassLoader() != null) {
      throw new IllegalStateException(
          "Agent loaded into class loader other than bootstrap: " + agentClass.getClassLoader());
    }
  }
}

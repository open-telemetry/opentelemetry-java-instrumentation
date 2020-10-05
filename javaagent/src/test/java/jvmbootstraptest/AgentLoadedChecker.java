/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

import java.net.URL;
import java.net.URLClassLoader;

public class AgentLoadedChecker {
  public static void main(String[] args) throws ClassNotFoundException {
    // Empty classloader that delegates to bootstrap
    URLClassLoader emptyClassLoader = new URLClassLoader(new URL[] {}, null);
    Class agentClass =
        emptyClassLoader.loadClass("io.opentelemetry.javaagent.bootstrap.AgentInitializer");

    if (agentClass.getClassLoader() != null) {
      throw new RuntimeException(
          "Agent loaded into classloader other than bootstrap: " + agentClass.getClassLoader());
    }
  }
}

package jvmbootstraptest;

import java.net.URL;
import java.net.URLClassLoader;

public class AgentLoadedChecker {
  public static void main(final String[] args) throws ClassNotFoundException {
    // Empty classloader that delegates to bootstrap
    final URLClassLoader emptyClassLoader = new URLClassLoader(new URL[] {}, null);
    final Class agentClass = emptyClassLoader.loadClass("datadog.trace.bootstrap.Agent");

    if (agentClass.getClassLoader() != null) {
      throw new RuntimeException(
          "Agent loaded into classloader other than bootstrap: " + agentClass.getClassLoader());
    }
  }
}

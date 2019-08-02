package jvmbootstraptest;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

public class AgentLoadedChecker {
  public static void main(final String[] args) throws ClassNotFoundException {
    System.out.println(Arrays.toString(args));

    // Empty classloader that delegates to bootstrap
    final URLClassLoader emptyClassLoader = new URLClassLoader(new URL[] {}, null);
    final Class agentClass = emptyClassLoader.loadClass("datadog.trace.agent.TracingAgent");

    if (agentClass.getClassLoader() != null) {
      throw new RuntimeException(
          "TracingAgent loaded into classloader other than bootstrap: "
              + agentClass.getClassLoader());
    }
  }
}

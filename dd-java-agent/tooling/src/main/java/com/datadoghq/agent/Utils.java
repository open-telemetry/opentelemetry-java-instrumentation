package com.datadoghq.agent;

public class Utils {
  /** Return the classloader the core agent is running on. */
  public static ClassLoader getAgentClassLoader() {
    return AgentInstaller.class.getClassLoader();
  }

  /** com.foo.Bar -> com/foo/Bar.class */
  public static String getResourceName(String className) {
    return className.replace('.', '/') + ".class";
  }

  private Utils() {}
}

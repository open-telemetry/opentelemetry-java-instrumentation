package jvmbootstraptest;

import java.util.logging.LogManager;

public class LogManagerSetter {
  public static void main(final String... args) throws Exception {
    if (System.getProperty("dd.app.customlogmanager") != null) {
      System.out.println("dd.app.customlogmanager != null");

      if (Boolean.valueOf(System.getProperty("dd.app.customlogmanager"))) {
        System.setProperty("java.util.logging.manager", CustomLogManager.class.getName());
        customAssert(
            LogManager.getLogManager().getClass(),
            LogManagerSetter.class
                .getClassLoader()
                .loadClass(System.getProperty("java.util.logging.manager")),
            "Javaagent should not prevent setting a custom log manager");
      } else {
        customAssert(
            isTracerInstalled(false),
            true,
            "tracer should be installed in premain when customlogmanager=false.");
        customAssert(
            isJmxfetchStarted(false),
            true,
            "jmxfetch should start in premain when customlogmanager=false.");
      }
    } else if (System.getProperty("java.util.logging.manager") != null) {
      System.out.println("java.util.logging.manager != null");

      if (ClassLoader.getSystemResource(
              System.getProperty("java.util.logging.manager").replaceAll("\\.", "/") + ".class")
          == null) {
        assertTraceInstallationDelayed(
            "tracer install must be delayed when log manager system property is present.");
        customAssert(
            isJmxfetchStarted(false),
            false,
            "jmxfetch startup must be delayed when log manager system property is present.");
        // Change back to a valid LogManager.
        System.setProperty("java.util.logging.manager", CustomLogManager.class.getName());
        customAssert(
            LogManager.getLogManager().getClass(),
            LogManagerSetter.class
                .getClassLoader()
                .loadClass(System.getProperty("java.util.logging.manager")),
            "Javaagent should not prevent setting a custom log manager");
        customAssert(
            isTracerInstalled(true), true, "tracer should be installed after loading LogManager.");
        customAssert(
            isJmxfetchStarted(true), true, "jmxfetch should start after loading LogManager.");
      } else {
        customAssert(
            isTracerInstalled(false),
            true,
            "tracer should be installed in premain when custom log manager found on classpath.");
        customAssert(
            isJmxfetchStarted(false),
            true,
            "jmxfetch should start in premain when custom log manager found on classpath.");
      }
    } else if (System.getenv("JBOSS_HOME") != null) {
      System.out.println("JBOSS_HOME != null");
      assertTraceInstallationDelayed(
          "tracer install must be delayed when JBOSS_HOME property is present.");
      customAssert(
          isJmxfetchStarted(false),
          false,
          "jmxfetch startup must be delayed when JBOSS_HOME property is present.");

      System.setProperty("java.util.logging.manager", CustomLogManager.class.getName());
      customAssert(
          LogManager.getLogManager().getClass(),
          LogManagerSetter.class
              .getClassLoader()
              .loadClass(System.getProperty("java.util.logging.manager")),
          "Javaagent should not prevent setting a custom log manager");
      customAssert(
          isTracerInstalled(true),
          true,
          "tracer should be installed after loading with JBOSS_HOME set.");
      customAssert(
          isJmxfetchStarted(true),
          true,
          "jmxfetch should start after loading with JBOSS_HOME set.");
    } else {
      System.out.println("No custom log manager");

      customAssert(
          isTracerInstalled(false),
          true,
          "tracer should be installed in premain when no custom log manager is set");
      customAssert(
          isJmxfetchStarted(false),
          true,
          "jmxfetch should start in premain when no custom log manager is set.");
    }
  }

  private static void customAssert(
      final Object got, final Object expected, final String assertionMessage) {
    if ((null == got && got != expected) || !got.equals(expected)) {
      throw new RuntimeException(
          "Assertion failed. Expected <" + expected + "> got <" + got + "> " + assertionMessage);
    }
  }

  private static void assertTraceInstallationDelayed(final String message) {
    if (isJavaBefore9WithJFR()) {
      customAssert(isTracerInstalled(false), false, message);
    } else {
      customAssert(
          isTracerInstalled(false),
          true,
          "We can safely install tracer on java9+ since it doesn't indirectly trigger logger manager init");
    }
  }

  private static boolean isJmxfetchStarted(final boolean wait) {
    // Wait up to 10 seconds for jmxfetch thread to appear
    for (int i = 0; i < 20; i++) {
      for (final Thread thread : Thread.getAllStackTraces().keySet()) {
        if ("dd-jmx-collector".equals(thread.getName())) {
          return true;
        }
      }
      if (!wait) {
        break;
      }
      try {
        Thread.sleep(500);
      } catch (final InterruptedException e) {
        e.printStackTrace();
      }
    }
    return false;
  }

  private static boolean isTracerInstalled(final boolean wait) {
    // Wait up to 10 seconds for tracer to get installed
    for (int i = 0; i < 20; i++) {
      if (io.opentracing.util.GlobalTracer.isRegistered()) {
        return true;
      }
      if (!wait) {
        break;
      }
      try {
        Thread.sleep(500);
      } catch (final InterruptedException e) {
        e.printStackTrace();
      }
    }
    return false;
  }

  private static boolean isJavaBefore9WithJFR() {
    if (!System.getProperty("java.version").startsWith("1.")) {
      return false;
    }

    final String jfrClassResourceName = "jdk.jfr.Recording".replace('.', '/') + ".class";
    return Thread.currentThread().getContextClassLoader().getResourceAsStream(jfrClassResourceName)
        != null;
  }
}

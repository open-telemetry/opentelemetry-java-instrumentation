package jvmbootstraptest;

import java.util.logging.LogManager;

public class LogManagerSetter {
  public static void main(final String... args) throws Exception {
    if (System.getProperty("dd.app.customlogmanager") != null) {
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
            isJmxfetchStarted(),
            true,
            "jmxfetch should start in premain when customlogmanager=false.");
      }
    } else if (System.getProperty("java.util.logging.manager") != null) {
      if (ClassLoader.getSystemResource(
              System.getProperty("java.util.logging.manager").replaceAll("\\.", "/") + ".class")
          == null) {
        customAssert(
            isJmxfetchStarted(),
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
        customAssert(isJmxfetchStarted(), true, "jmxfetch should start after loading LogManager.");
      } else {
        customAssert(
            isJmxfetchStarted(),
            true,
            "jmxfetch should start in premain when custom log manager found on classpath.");
      }
    } else if (System.getenv("JBOSS_HOME") != null) {
      System.setProperty("java.util.logging.manager", CustomLogManager.class.getName());
      customAssert(
          LogManager.getLogManager().getClass(),
          LogManagerSetter.class
              .getClassLoader()
              .loadClass(System.getProperty("java.util.logging.manager")),
          "Javaagent should not prevent setting a custom log manager");

    } else {
      customAssert(
          isJmxfetchStarted(),
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

  private static boolean isJmxfetchStarted() {
    for (final Thread thread : Thread.getAllStackTraces().keySet()) {
      if ("dd-jmx-collector".equals(thread.getName())) {
        return true;
      }
    }
    return false;
  }
}

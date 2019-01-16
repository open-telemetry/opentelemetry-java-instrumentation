package jvmbootstraptest;

import java.util.logging.LogManager;

public class LogManagerSetter {
  public static void main(String... args) throws Exception {
    if (System.getProperty("java.util.logging.manager") != null) {
      customAssert(
          isJmxfetchStarted(),
          false,
          "jmxfetch startup must be delayed when log manager system property is present.");
      customAssert(
          LogManager.getLogManager().getClass(),
          LogManager.class
              .getClassLoader()
              .loadClass(System.getProperty("java.util.logging.manager")),
          "Javaagent should not prevent setting a custom log manager");
      customAssert(isJmxfetchStarted(), true, "jmxfetch should start after loading LogManager.");
    } else if (System.getProperty("dd.app.customlogmanager") != null) {
      System.setProperty("java.util.logging.manager", CustomLogManager.class.getName());
      customAssert(
          LogManager.getLogManager().getClass(),
          LogManager.class
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

  private static void customAssert(Object got, Object expected, String assertionMessage) {
    if ((null == got && got != expected) || !got.equals(expected)) {
      throw new RuntimeException(
          "Assertion failed. Expected <" + expected + "> got <" + got + "> " + assertionMessage);
    }
  }

  private static boolean isJmxfetchStarted() {
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      if ("dd-jmx-collector".equals(thread.getName())) {
        return true;
      }
    }
    return false;
  }
}

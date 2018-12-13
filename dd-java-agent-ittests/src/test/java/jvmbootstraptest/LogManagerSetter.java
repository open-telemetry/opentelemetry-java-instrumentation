package jvmbootstraptest;

import java.lang.reflect.Method;
import java.util.logging.LogManager;

public class LogManagerSetter {
  // Intentionally doing the string replace to bypass gradle shadow rename
  // loggerClassName = java.util.logging.Logger
  private static final String loggerClassName =
      "java.util.logging.TMP".replaceFirst("TMP", "Logger");

  public static void main(String... args) throws Exception {
    final ClassLoader systemLoader = ClassLoader.getSystemClassLoader();

    // once the logger class has been initialized, we know jmxfetch is running and can proceed with
    // the test
    final Method method =
        ClassLoader.class.getDeclaredMethod("findBootstrapClassOrNull", String.class);
    try {
      method.setAccessible(true);
      while (method.invoke(systemLoader, loggerClassName) == null) {
        Thread.sleep(1);
      }
    } finally {
      method.setAccessible(false);
    }

    System.setProperty("java.util.logging.manager", CustomLogManager.class.getName());
    customAssert(
        LogManager.getLogManager().getClass(),
        CustomLogManager.class,
        "Javaagent should not prevent setting a custom log manager");
  }

  private static void customAssert(Object got, Object expected, String assertionMessage) {
    if ((null == got && got != expected) || !got.equals(expected)) {
      throw new RuntimeException(
          "Assertion failed. Expected <" + expected + "> got <" + got + "> " + assertionMessage);
    }
  }
}

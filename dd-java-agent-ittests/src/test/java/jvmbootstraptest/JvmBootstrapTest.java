package jvmbootstraptest;

import java.util.logging.LogManager;

public class JvmBootstrapTest {
  /**
   * See gradle task `jvmStartupTest`. This runs a paired-down jvm in order to assert that the
   * javaagent initializes correctly without interference by gradle/junit/spock.
   */
  public static void main(String... args) throws Exception {
    // Sleeping here to give the agent more time to initialize and potentially do something wrong.
    // This is an attempt to be as mean to the agent as possible.
    Thread.sleep(2000);
    final CustomClassloader systemLoader = (CustomClassloader) ClassLoader.getSystemClassLoader();

    // sync around the classloading lock to guarantee datadog threads won't be able to see the
    // logging manager property if they're currently initializing the logging manager.
    synchronized (systemLoader.getClassLoadingLock("java.util.logging.LogManager")) {
      System.setProperty("java.util.logging.manager", CustomLogManager.class.getName());
      customAssert(
          LogManager.getLogManager().getClass() == CustomLogManager.class,
          true,
          "Javaagent should not prevent setting a custom log manager");
    }
  }

  private static void customAssert(boolean got, boolean expected, String assertionMessage) {
    if (got != expected) {
      throw new RuntimeException("Assertion failed: " + assertionMessage);
    }
  }
}

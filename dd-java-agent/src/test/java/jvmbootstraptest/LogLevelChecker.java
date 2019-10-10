package jvmbootstraptest;

public class LogLevelChecker {
  // returns an exception if logs are not in DEBUG
  public static void main(final String[] args) throws ClassNotFoundException {

    String str = System.getProperty("datadog.slf4j.simpleLogger.defaultLogLevel");

    if ((str == null) || (str != null && !str.equalsIgnoreCase("debug"))) {
      throw new RuntimeException("debug mode not set");
    }
  }
}

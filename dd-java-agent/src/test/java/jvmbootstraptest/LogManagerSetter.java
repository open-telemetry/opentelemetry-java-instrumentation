package jvmbootstraptest;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.LogManager;

public class LogManagerSetter {
  // Intentionally doing the string replace to bypass gradle shadow rename
  // loggerClassName = java.util.logging.Logger
  private static final String loggerClassName =
      "java.util.logging.TMP".replaceFirst("TMP", "Logger");
  private static final String loggerInternalName = loggerClassName.replace('.', '/');
  private static final AtomicBoolean loggerInitialized = new AtomicBoolean(false);

  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    inst.addTransformer(
        new ClassFileTransformer() {
          @Override
          public byte[] transform(
              ClassLoader loader,
              String className,
              Class<?> classBeingRedefined,
              ProtectionDomain protectionDomain,
              byte[] classfileBuffer)
              throws IllegalClassFormatException {
            if (loggerInternalName.equals(className)) {
              loggerInitialized.compareAndSet(false, true);
            }
            return null;
          }
        });
  }

  public static void main(String... args) throws Exception {
    final ClassLoader systemLoader = ClassLoader.getSystemClassLoader();

    // once the logger class has been initialized, we know jmxfetch is running and can proceed with
    // the test
    while (!loggerInitialized.get()) {
      Thread.sleep(1);
    }
    systemLoader.loadClass(loggerClassName);
    // at this point the logger is loaded and fully initialized

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

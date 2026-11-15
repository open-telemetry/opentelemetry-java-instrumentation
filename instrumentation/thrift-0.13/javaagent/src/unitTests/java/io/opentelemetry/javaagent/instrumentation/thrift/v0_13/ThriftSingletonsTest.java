/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

class ThriftSingletonsTest {

  @Test
  void continuesSuperclassLookupAfterGenericInspectionFails() throws Exception {
    Object processor = brokenProcessor(BrokenSubclassProcessor.class);

    assertGenericInspectionFails(processor);

    assertThat(ThriftSingletons.getServerServiceName(processor, new Handler()))
        .isEqualTo(Service.class.getName());
  }

  @Test
  void fallsBackToHandlerNameAfterGenericInspectionFails() throws Exception {
    Object processor = brokenProcessor(BrokenProcessor.class);

    assertGenericInspectionFails(processor);

    assertThat(ThriftSingletons.getServerServiceName(processor, new Handler()))
        .isEqualTo(Handler.class.getName());
  }

  private static void assertGenericInspectionFails(Object processor) {
    assertThatThrownBy(() -> processor.getClass().getTypeParameters()[0].getBounds())
        .isInstanceOf(TypeNotPresentException.class);
  }

  private static Object brokenProcessor(Class<?> brokenProcessorClass) throws Exception {
    String className = brokenProcessorClass.getName();
    String enclosingClassName = ThriftSingletonsTest.class.getName();
    ClassLoader classLoader =
        new ClassLoader(ThriftSingletonsTest.class.getClassLoader()) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(MissingBound.class.getName())) {
              throw new ClassNotFoundException(name);
            }
            if (!name.equals(enclosingClassName) && !name.startsWith(enclosingClassName + "$")) {
              return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
              Class<?> loaded = findLoadedClass(name);
              if (loaded == null) {
                try {
                  byte[] classBytes = readClassBytes(name);
                  loaded = defineClass(name, classBytes, 0, classBytes.length);
                } catch (IOException e) {
                  throw new ClassNotFoundException(name, e);
                }
              }
              if (resolve) {
                resolveClass(loaded);
              }
              return loaded;
            }
          }
        };
    Constructor<?> constructor = classLoader.loadClass(className).getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }

  private static byte[] readClassBytes(String className) throws IOException {
    try (InputStream input =
        ThriftSingletonsTest.class.getResourceAsStream(
            "/" + className.replace('.', '/') + ".class")) {
      if (input == null) {
        throw new IllegalStateException("Class resource not found: " + className);
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      int value;
      while ((value = input.read()) != -1) {
        output.write(value);
      }
      return output.toByteArray();
    }
  }

  public static class Service {
    public interface Iface {}

    public static class Processor<I extends Iface> {}

    private Service() {}
  }

  private static class MissingBound {}

  private static class BrokenProcessor<I extends MissingBound> {}

  private static class BrokenSubclassProcessor<I extends MissingBound>
      extends Service.Processor<Service.Iface> {}

  private static class Handler {}
}

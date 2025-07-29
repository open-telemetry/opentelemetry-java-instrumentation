/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;

import io.opentelemetry.javaagent.bootstrap.DefineClassHelper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.ProtectionDomain;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class DefineClassInstrumentationTest {

  public static class DefiningClassLoader extends ClassLoader {

    public DefiningClassLoader() {
      super(null);
    }

    // Suppressing warnings to force testing of deprecated method
    @SuppressWarnings("deprecation")
    public Class<?> doDefineClass(byte[] b, int off, int len) {
      return defineClass(b, off, len);
    }

    public Class<?> doDefineClass(String name, byte[] b, int off, int len) {
      return defineClass(name, b, off, len);
    }

    public Class<?> doDefineClass(
        String name, byte[] b, int off, int len, ProtectionDomain protectionDomain) {
      return defineClass(name, b, off, len, protectionDomain);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {3, 4, 5})
  @SuppressWarnings("DirectInvocationOnMock")
  void ensureDefineClassInstrumented(int argCount) throws IOException {
    String className = Dummy.class.getName();
    String resource = className.replace('.', '/') + ".class";
    ByteArrayOutputStream byteCodeStream = new ByteArrayOutputStream();
    try (InputStream classfile = getClass().getResourceAsStream("/" + resource)) {
      IOUtils.copy(classfile, byteCodeStream, 1024);
    }
    byte[] bytecode = byteCodeStream.toByteArray();

    DefiningClassLoader cl = new DefiningClassLoader();

    DefineClassHelper.Handler mockHandler = Mockito.mock(DefineClassHelper.Handler.class);
    // We need to initialize mockHandler by invoking it early, otherwise this leads to problems
    mockHandler.beforeDefineClass(cl, className, bytecode, 0, bytecode.length);
    Mockito.reset(mockHandler);

    DefineClassHelper.Handler originalHandler =
        DefineClassHelper.internalSetHandlerForTests(mockHandler);
    String expectedClassName;
    try {
      switch (argCount) {
        case 3:
          expectedClassName = null;
          cl.doDefineClass(bytecode, 0, bytecode.length);
          break;
        case 4:
          expectedClassName = className;
          cl.doDefineClass(className, bytecode, 0, bytecode.length);
          break;
        case 5:
          expectedClassName = className;
          cl.doDefineClass(className, bytecode, 0, bytecode.length, null);
          break;
        default:
          throw new IllegalStateException();
      }
    } finally {
      DefineClassHelper.internalSetHandlerForTests(originalHandler);
    }

    verify(mockHandler)
        .beforeDefineClass(
            same(cl), eq(expectedClassName), eq(bytecode), eq(0), eq(bytecode.length));
    verify(mockHandler).afterDefineClass(eq(null));
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.bootstrap.DefineClassHelper;
import io.opentelemetry.javaagent.bootstrap.DefineClassHelper.Handler.DefineClassContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;

class DefineClassInstrumentationTest {

  static class DefiningClassLoader extends ClassLoader {

    DefiningClassLoader() {
      super(null);
    }

    // Suppressing warnings to force testing of deprecated method
    @SuppressWarnings("deprecation")
    Class<?> doDefineClass(byte[] b, int off, int len) {
      return defineClass(b, off, len);
    }

    Class<?> doDefineClass(String name, byte[] b, int off, int len) {
      return defineClass(name, b, off, len);
    }

    Class<?> doDefineClass(
        String name, byte[] b, int off, int len, ProtectionDomain protectionDomain) {
      return defineClass(name, b, off, len, protectionDomain);
    }

    Class<?> doDefineClass(String name, ByteBuffer b, ProtectionDomain protectionDomain) {
      return defineClass(name, b, protectionDomain);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"byteArray3", "byteArray4", "byteArray5", "byteBuffer3"})
  @SuppressWarnings("DirectInvocationOnMock")
  void ensureDefineClassInstrumented(String signature) throws IOException {
    String className = Dummy.class.getName();
    String resource = className.replace('.', '/') + ".class";
    String expectedClassName = signature.equals("byteArray3") ? null : className;
    ByteArrayOutputStream byteCodeStream = new ByteArrayOutputStream();
    try (InputStream classfile = getClass().getResourceAsStream("/" + resource)) {
      IOUtils.copy(classfile, byteCodeStream, 1024);
    }
    byte[] bytecode = byteCodeStream.toByteArray();

    DefiningClassLoader cl = new DefiningClassLoader();

    DefineClassHelper.Handler mockHandler = mock(DefineClassHelper.Handler.class);
    // We need to initialize mockHandler by invoking it early, otherwise this leads to problems
    mockHandler.beforeDefineClass(cl, className, bytecode, 0, bytecode.length);
    Mockito.reset(mockHandler);

    DefineClassContext context = mock(DefineClassContext.class);
    when(mockHandler.beforeDefineClass(cl, expectedClassName, bytecode, 0, bytecode.length))
        .thenReturn(context);
    mockHandler.beforeDefineClass(cl, expectedClassName, bytecode, 0, bytecode.length);
    Mockito.clearInvocations(mockHandler);

    DefineClassHelper.Handler originalHandler =
        DefineClassHelper.internalSetHandlerForTests(mockHandler);
    Class<?> definedClass;
    try {
      switch (signature) {
        case "byteArray3":
          definedClass = cl.doDefineClass(bytecode, 0, bytecode.length);
          break;
        case "byteArray4":
          definedClass = cl.doDefineClass(className, bytecode, 0, bytecode.length);
          break;
        case "byteArray5":
          definedClass = cl.doDefineClass(className, bytecode, 0, bytecode.length, null);
          break;
        case "byteBuffer3":
          ByteBuffer buffer = ByteBuffer.allocate(bytecode.length);
          buffer.put(bytecode);
          buffer.flip();
          definedClass = cl.doDefineClass(className, buffer, null);
          break;
        default:
          throw new IllegalStateException();
      }
    } finally {
      DefineClassHelper.internalSetHandlerForTests(originalHandler);
    }

    assertThat(definedClass.getName()).isEqualTo(className);
    assertThat(definedClass.getClassLoader()).isSameAs(cl);
    // defineClass that accepts ByteBuffer delegates to the one that accepts byte array
    VerificationMode verificationMode = times(signature.equals("byteBuffer3") ? 2 : 1);
    verify(mockHandler, verificationMode)
        .beforeDefineClass(
            same(cl), eq(expectedClassName), eq(bytecode), eq(0), eq(bytecode.length));
    verify(mockHandler, verificationMode).afterDefineClass(same(context));
  }
}

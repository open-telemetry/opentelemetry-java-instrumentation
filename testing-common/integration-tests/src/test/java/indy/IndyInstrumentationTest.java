/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package indy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import library.MyProxySuperclass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

@SuppressWarnings({"unused", "MethodCanBeStatic"})
public class IndyInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private String privateField;

  // The following methods are instrumented by the IndyInstrumentationTestModule

  private void assignToFieldViaReturn(String toAssign) {}

  private void assignToFieldViaArray(String toAssign) {}

  private String assignToArgumentViaReturn(String a, String toAssign) {
    return "Arg:" + a;
  }

  private String assignToArgumentViaArray(String a, String toAssign) {
    return "Arg:" + a;
  }

  private String assignToReturnViaReturn(String toAssign) {
    return "replace_me";
  }

  private String assignToReturnViaArray(String toAssign) {
    return "replace_me";
  }

  private String noExceptionPlease(String s) {
    return s + "_no_exception";
  }

  private void exceptionPlease() {}

  private Class<?> getHelperClass(boolean local) {
    return null;
  }

  private Object instrumentWithErasedTypes() {
    return "replace_me";
  }

  @AfterEach
  public void reset() {
    privateField = null;
  }

  @Test
  void testAssignToFieldViaReturn() {
    assignToFieldViaReturn("field_val");
    assertThat(privateField).isEqualTo("field_val");
  }

  @Test
  void testAssignToFieldViaArray() {
    assignToFieldViaArray("array_field_val");
    assertThat(privateField).isEqualTo("array_field_val");
  }

  @Test
  void testAssignToArgumentViaReturn() {
    String value = assignToArgumentViaReturn("", "arg_val");
    assertThat(value).isEqualTo("Arg:arg_val");
  }

  @Test
  void testAssignToArgumentViaArray() {
    String value = assignToArgumentViaArray("", "arg_array_val");
    assertThat(value).isEqualTo("Arg:arg_array_val");
  }

  @Test
  void testAssignToReturnViaReturn() {
    String value = assignToReturnViaReturn("ret_val");
    assertThat(value).isEqualTo("ret_val");
  }

  @Test
  void testAssignToReturnViaArray() {
    String value = assignToReturnViaReturn("ret_array_val");
    assertThat(value).isEqualTo("ret_array_val");
  }

  @Test
  void testSuppressException() {
    assertThat(noExceptionPlease("foo")).isEqualTo("foo_no_exception");
    assertThat(TestAgentListenerAccess.getAndResetAdviceFailureCount()).isEqualTo(2);
  }

  @Test
  void testThrowExceptionIntoUserCode() {
    assertThatThrownBy(this::exceptionPlease).isInstanceOf(RuntimeException.class);
  }

  @Test
  void testAdviceSignatureReferenceInternalHelper() {
    Object result = instrumentWithErasedTypes();
    assertThat(result.getClass().getName()).contains("LocalHelper");
  }

  @Test
  void testHelperClassLoading() {
    Class<?> localHelper = getHelperClass(true);
    assertThat(localHelper.getName()).endsWith("LocalHelper");
    assertThat(localHelper.getClassLoader().getClass().getName())
        .endsWith("InstrumentationModuleClassLoader");

    Class<?> globalHelper = getHelperClass(false);
    assertThat(globalHelper.getName()).endsWith("GlobalHelper");
    assertThat(globalHelper.getClassLoader().getClass().getName()).endsWith("AgentClassLoader");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testProxyInjection() throws Exception {
    Class<?> proxyClass = Class.forName("foo.bar.Proxy");

    // create an instance and invoke static & non-static methods
    // this verifies that our invokedynamic bootstrapping works for constructors, static and
    // non-static methods

    Object proxyInstance = proxyClass.getConstructor().newInstance();
    assertThat(proxyInstance).isInstanceOf(Callable.class);
    assertThat(proxyInstance).isInstanceOf(MyProxySuperclass.class);

    String invocResult = ((Callable<String>) proxyInstance).call();
    assertThat(invocResult).isEqualTo("Hi from ProxyMe");

    String staticResult = (String) proxyClass.getMethod("staticHello").invoke(null);
    assertThat(staticResult).isEqualTo("Hi from static");

    Field delegateField = proxyClass.getDeclaredField("delegate");
    delegateField.setAccessible(true);
    Object delegate = delegateField.get(proxyInstance);

    ClassLoader delegateCl = delegate.getClass().getClassLoader();
    assertThat(delegate.getClass().getName()).isEqualTo("indy.ProxyMe");
    assertThat(delegateCl.getClass().getName()).endsWith("InstrumentationModuleClassLoader");

    // Ensure that the bytecode of the proxy is injected as a resource
    InputStream res =
        IndyInstrumentationTest.class.getClassLoader().getResourceAsStream("foo/bar/Proxy.class");
    byte[] bytecode = IOUtils.toByteArray(res);
    assertThat(bytecode).startsWith(0xCA, 0xFE, 0xBA, 0xBE);
  }
}

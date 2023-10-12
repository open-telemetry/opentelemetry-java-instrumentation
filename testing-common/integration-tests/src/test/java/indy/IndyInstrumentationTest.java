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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
  void testHelperClassLoading() {
    Class<?> localHelper = getHelperClass(true);
    assertThat(localHelper.getName()).endsWith("LocalHelper");
    assertThat(localHelper.getClassLoader().getClass().getName())
        .endsWith("InstrumentationModuleClassLoader");

    Class<?> globalHelper = getHelperClass(false);
    assertThat(globalHelper.getName()).endsWith("GlobalHelper");
    assertThat(globalHelper.getClassLoader().getClass().getName()).endsWith("AgentClassLoader");
  }
}

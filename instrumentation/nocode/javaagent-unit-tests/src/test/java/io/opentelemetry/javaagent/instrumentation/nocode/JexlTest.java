/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class JexlTest {
  private static final Map<String, String> thiz = new HashMap<>();
  private static final Set<String> param0 = new HashSet<>();
  private static final Map<String, Object> param1 = new HashMap<>();

  static {
    thiz.put("key", "value");
    param0.add("present");
    param1.put("float", 7.0f);
  }

  private JexlTest() {}

  @SuppressWarnings("rawtypes")
  private static Object evalJexl(String jexl, Object thiz, Object[] params) {
    return new JexlEvaluator().evaluate(jexl, thiz, params);
  }

  static Stream<Arguments> jexlToExpected() {
    return Stream.of(
        arguments("this", "{key=value}"),
        arguments("this.toString()", "{key=value}"),
        arguments("this.toString().length()", 11),
        arguments("this.get(\"key\")", "value"),
        arguments("this.get(\"key\").substring(1)", "alue"),
        arguments("param0.isEmpty()", false),
        arguments("param0.contains(\"present\")", true),
        arguments("\"prefix: \"+this.toString()+\" (suffix)\"", "prefix: {key=value} (suffix)"),
        arguments("param1.get(\"float\")", 7.0f),
        arguments("this.entrySet().size()", 1));
  }

  @ParameterizedTest
  @MethodSource("jexlToExpected")
  void testBasicBehavior(String jexl, Object expected) {
    Object result = evalJexl(jexl, thiz, new Object[] {param0, param1});
    if (expected instanceof String) {
      result = result == null ? null : result.toString();
    }
    assertEquals(expected, result, jexl);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "nosuchvar",
        "nosuchvar.toString()",
        "this  .",
        "this  .  ",
        "this.noSuchMethod()",
        "this.toString()extrastuffatend",
        "this.toString()toString()",
        "param1.toString()", // out of bounds
        "param999.toString()",
        "this.get(\"noclosequote)",
        "this.get(\"nocloseparan\"",
        "this.noparens",
        "this.noparens.anotherMethod()",
        "this.wrongOrder)(",
        "this.get(NotALiteralParameter);",
        "this.get(12.2)",
        "this.get(this)",
        "this.get(\"NoSuchKey\")", // evals completely but returns null
        "param1.toString()", // no such param
      })
  void testInvalidJexlReturnNull(String invalid) {
    Object answer = evalJexl(invalid, thiz, new Object[] {param0});
    assertNull(answer, "Expected null for \"" + invalid + "\" but was \"" + answer + "\"");
  }

  @Test
  void testIntegerLiteralLongerThanOneDigit() {
    Map<String, String> o = new HashMap<>();
    o.put("key", "really long value");
    String jexl = "this.get(\"key\").substring(10)";
    assertEquals("g value", evalJexl(jexl, o, new Object[0]));
  }

  public static class TakeString {
    public String take(String s) {
      return s;
    }
  }

  public static class TakeObject {
    public String take(Object o) {
      return o.toString();
    }
  }

  public static class TakeBooleanPrimitive {
    public String take(boolean param) {
      return Boolean.toString(param);
    }
  }

  public static class TakeBoolean {
    public String take(Boolean param) {
      return param.toString();
    }
  }

  public static class TakeIntegerPrimitive {
    public String take(int param) {
      return Integer.toString(param);
    }
  }

  public static class TakeInteger {
    public String take(Integer param) {
      return param.toString();
    }
  }

  public static class TakeLongPrimitize {
    public String take(long param) {
      return Long.toString(param);
    }
  }

  public static class TakeLong {
    public String take(Long param) {
      return param.toString();
    }
  }

  @Test
  void testBooleanLiteralParamTypes() {
    assertEquals("true", evalJexl("this.take(true)", new TakeBooleanPrimitive(), new Object[0]));
    assertEquals("false", evalJexl("this.take(false)", new TakeBoolean(), new Object[0]));
    assertEquals("true", evalJexl("this.take(true)", new TakeObject(), new Object[0]));
  }

  @Test
  void testStringLiteralParamTypes() {
    assertEquals("a", evalJexl("this.take(\"a\")", new TakeString(), new Object[0]));
    assertEquals("a", evalJexl("this.take(\"a\")", new TakeObject(), new Object[0]));
  }

  @Test
  public void testIntegerLiteralParamTypes() {
    assertEquals("13", evalJexl("this.take(13)", new TakeIntegerPrimitive(), new Object[0]));
    assertEquals("13", evalJexl("this.take(13)", new TakeInteger(), new Object[0]));
    assertEquals("13", evalJexl("this.take(13)", new TakeLongPrimitize(), new Object[0]));
    // Just documenting some oddness here with the long treatment
    assertEquals("13", evalJexl("this.take(13L)", new TakeLong(), new Object[0]));
    assertEquals("13", evalJexl("this.take(13)", new TakeObject(), new Object[0]));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "this.get(\"key\").substring(1)",
        " this.get(\"key\").substring(1)",
        "this .get(\"key\").substring(1)",
        "this. get(\"key\").substring(1)",
        "this.get (\"key\").substring(1)",
        "this.get( \"key\").substring(1)",
        "this.get(\"key\" ).substring(1)",
        "this.get(\"key\")\t.substring(1)",
        "this.get(\"key\").\nsubstring(1)",
        "this.get(\"key\").substring\r(1)",
        "this.get(\"key\").substring( 1)",
        "this.get(\"key\").substring(1 )",
      })
  void testWhitespace(String test) {
    assertEquals("alue", evalJexl(test, thiz, new Object[] {param0}), test);
  }

  @Test
  void testManyParams() {
    Object[] params = new Object[13];
    Arrays.fill(params, new Object());
    assertEquals(
        "java.lang.Object", evalJexl("param12.getClass().getName()", new Object(), params));
  }

  @Test
  void testTernaryLogic() {
    assertEquals(
        "potato", evalJexl("this.size() > 0 ? \"potato\" : \"banana\"", thiz, new Object[] {}));
    assertEquals(
        "banana", evalJexl("this.size() < 0 ? \"potato\" : \"banana\"", thiz, new Object[] {}));
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.code;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FILE_PATH;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static io.opentelemetry.semconv.CodeAttributes.CODE_LINE_NUMBER;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AbstractLongAssert;

/** Assertions for source code attributes. */
public final class CodeAssertions {

  public static List<AttributeAssertion> codeFunctionAssertions(Class<?> type, String methodName) {
    return codeFunctionAssertions(type.getName(), methodName);
  }

  public static List<AttributeAssertion> codeFunctionAssertions(String type, String methodName) {
    return new ArrayList<>(singletonList(equalTo(CODE_FUNCTION_NAME, type + "." + methodName)));
  }

  public static List<AttributeAssertion> codeFileAndLineAssertions(String filePath) {
    return new ArrayList<>(
        asList(
            equalTo(CODE_FILE_PATH, filePath),
            satisfies(CODE_LINE_NUMBER, AbstractLongAssert::isPositive)));
  }

  public static List<AttributeAssertion> codeFunctionSuffixAssertions(String methodName) {
    return internalFunctionAssert(v -> v.endsWith("." + methodName));
  }

  public static List<AttributeAssertion> codeFunctionSuffixAssertions(
      String namespaceSuffix, String methodName) {
    return internalFunctionAssert(v -> v.endsWith(namespaceSuffix + "." + methodName));
  }

  public static List<AttributeAssertion> codeFunctionInfixAssertions(
      String namespaceInfix, String methodName) {
    return internalFunctionAssert(v -> v.contains(namespaceInfix).endsWith("." + methodName));
  }

  public static List<AttributeAssertion> codeFunctionPrefixAssertions(
      String namespacePrefix, String methodName) {
    return internalFunctionAssert(v -> v.startsWith(namespacePrefix).endsWith(methodName));
  }

  private static List<AttributeAssertion> internalFunctionAssert(
      OpenTelemetryAssertions.StringAssertConsumer functionNameAssert) {
    return new ArrayList<>(singletonList(satisfies(CODE_FUNCTION_NAME, functionNameAssert)));
  }

  private CodeAssertions() {}
}

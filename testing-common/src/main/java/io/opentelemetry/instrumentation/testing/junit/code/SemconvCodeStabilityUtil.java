/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.code;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.semconv.CodeAttributes;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;

// until old code semconv are dropped in 3.0
public class SemconvCodeStabilityUtil {

  public static List<AttributeAssertion> codeFunctionAssertions(Class<?> type, String methodName) {
    return codeFunctionAssertions(type.getName(), methodName);
  }

  public static List<AttributeAssertion> codeFunctionAssertions(String type, String methodName) {
    return internalFunctionAssert(
        methodName, v -> v.isEqualTo(type + "." + methodName), v -> v.isEqualTo(type));
  }

  @SuppressWarnings("deprecation") // testing deprecated code semconv
  public static List<AttributeAssertion> codeFileAndLineAssertions(String filePath) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    if (SemconvStability.isEmitStableCodeSemconv()) {
      assertions.add(equalTo(CodeAttributes.CODE_FILE_PATH, filePath));
      assertions.add(satisfies(CodeAttributes.CODE_LINE_NUMBER, AbstractLongAssert::isPositive));
    }
    if (SemconvStability.isEmitOldCodeSemconv()) {
      assertions.add(equalTo(CodeIncubatingAttributes.CODE_FILEPATH, filePath));
      assertions.add(
          satisfies(CodeIncubatingAttributes.CODE_LINENO, AbstractLongAssert::isPositive));
    }

    return assertions;
  }

  public static List<AttributeAssertion> codeFunctionSuffixAssertions(String methodName) {
    return internalFunctionAssert(
        methodName, v -> v.endsWith("." + methodName), AbstractStringAssert::isNotEmpty);
  }

  public static List<AttributeAssertion> codeFunctionSuffixAssertions(
      String namespaceSuffix, String methodName) {
    return internalFunctionAssert(
        methodName,
        v -> v.endsWith(namespaceSuffix + "." + methodName),
        v -> v.endsWith(namespaceSuffix));
  }

  public static List<AttributeAssertion> codeFunctionInfixAssertions(
      String namespaceInfix, String methodName) {
    return internalFunctionAssert(
        methodName,
        v -> v.contains(namespaceInfix).endsWith("." + methodName),
        v -> v.contains(namespaceInfix));
  }

  public static List<AttributeAssertion> codeFunctionPrefixAssertions(
      String namespacePrefix, String methodName) {
    return internalFunctionAssert(
        methodName,
        v -> v.startsWith(namespacePrefix).endsWith(methodName),
        v -> v.startsWith(namespacePrefix));
  }

  @SuppressWarnings("deprecation") // testing deprecated code semconv
  private static List<AttributeAssertion> internalFunctionAssert(
      String methodName,
      // CHECKSTYLE:OFF
      OpenTelemetryAssertions.StringAssertConsumer functionNameAssert,
      OpenTelemetryAssertions.StringAssertConsumer namespaceAssert
      // CHECKSTYLE:ON
      ) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    if (SemconvStability.isEmitStableCodeSemconv()) {
      assertions.add(satisfies(CodeAttributes.CODE_FUNCTION_NAME, functionNameAssert));
    }
    if (SemconvStability.isEmitOldCodeSemconv()) {
      assertions.add(equalTo(CodeIncubatingAttributes.CODE_FUNCTION, methodName));
      assertions.add(satisfies(CodeIncubatingAttributes.CODE_NAMESPACE, namespaceAssert));
    }
    return assertions;
  }

  public static int codeAttributesLogCount() {
    int count = 0;
    if (SemconvStability.isEmitOldCodeSemconv()) {
      count += 4;
    }
    if (SemconvStability.isEmitStableCodeSemconv()) {
      count += 3;
    }
    return count;
  }

  private SemconvCodeStabilityUtil() {}
}

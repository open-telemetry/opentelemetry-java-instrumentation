/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.code;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
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

  @SuppressWarnings("deprecation") // testing deprecated code semconv
  public static List<AttributeAssertion> codeFunctionAssertions(String type, String methodName) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    if (SemconvStability.isEmitStableCodeSemconv()) {
      assertions.add(equalTo(CodeAttributes.CODE_FUNCTION_NAME, type + "." + methodName));
    }
    if (SemconvStability.isEmitOldCodeSemconv()) {
      assertions.add(equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, type));
      assertions.add(equalTo(CodeIncubatingAttributes.CODE_FUNCTION, methodName));
    }
    return assertions;
  }

  @SuppressWarnings("deprecation") // testing deprecated code semconv
  public static List<AttributeAssertion> codeFileAndLineAssertions(String filePath) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    if (SemconvStability.isEmitStableCodeSemconv()) {
      assertions.add(equalTo(CodeAttributes.CODE_FILE_PATH, filePath));
      assertions.add(satisfies(CodeAttributes.CODE_LINE_NUMBER, AbstractLongAssert::isPositive));
    }
    if (SemconvStability.isEmitOldCodeSemconv()) {
      assertions.add(equalTo(CodeIncubatingAttributes.CODE_FILE_PATH, filePath));
      assertions.add(
          satisfies(CodeIncubatingAttributes.CODE_LINE_NUMBER, AbstractLongAssert::isPositive));
    }

    return assertions;
  }

  @SuppressWarnings("deprecation") // testing deprecated code semconv
  public static List<AttributeAssertion> codeAttributeSuffixAssertions(String methodName) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    if (SemconvStability.isEmitStableCodeSemconv()) {
      assertions.add(
          satisfies(CodeAttributes.CODE_FUNCTION_NAME, v -> v.endsWith("." + methodName)));
    }
    if (SemconvStability.isEmitOldCodeSemconv()) {
      assertions.add(equalTo(CodeIncubatingAttributes.CODE_FUNCTION, methodName));
      assertions.add(
          satisfies(CodeIncubatingAttributes.CODE_NAMESPACE, AbstractStringAssert::isNotEmpty));
    }
    return assertions;
  }

  @SuppressWarnings("deprecation") // testing deprecated code semconv
  public static List<AttributeAssertion> codeAttributeSuffixAssertions(
      String namespaceSuffix, String methodName) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    if (SemconvStability.isEmitStableCodeSemconv()) {
      assertions.add(
          satisfies(CodeAttributes.CODE_FUNCTION_NAME, v -> v.endsWith("." + methodName)));
    }
    if (SemconvStability.isEmitOldCodeSemconv()) {
      assertions.add(equalTo(CodeIncubatingAttributes.CODE_FUNCTION, methodName));
      assertions.add(
          satisfies(CodeIncubatingAttributes.CODE_NAMESPACE, v -> v.endsWith(namespaceSuffix)));
    }
    return assertions;
  }

  private SemconvCodeStabilityUtil() {}
}

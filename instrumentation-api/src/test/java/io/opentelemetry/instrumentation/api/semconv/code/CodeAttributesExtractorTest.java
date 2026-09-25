/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.code;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldCodeSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableCodeSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static java.util.Collections.emptyMap;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeAttributesExtractorTest {

  static class TestAttributesGetter implements CodeAttributesGetter<Map<String, String>> {
    @Override
    public Class<?> getCodeClass(Map<String, String> request) {
      return request.containsKey("class") ? TestClass.class : null;
    }

    @Override
    public String getMethodName(Map<String, String> request) {
      return request.get("methodName");
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void extractsCodeAttributes() {
    Map<String, String> request = new HashMap<>();
    request.put("class", "present");
    request.put("methodName", "doSomething");
    AttributesExtractor<Map<String, String>, Void> extractor =
        CodeAttributesExtractor.create(new TestAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);

    AttributesBuilder expected = Attributes.builder();
    if (emitOldCodeSemconv()) {
      expected.put(CODE_NAMESPACE, TestClass.class.getName());
      expected.put(CODE_FUNCTION, "doSomething");
    }
    if (emitStableCodeSemconv()) {
      expected.put(CODE_FUNCTION_NAME, TestClass.class.getName() + ".doSomething");
    }
    assertThat(startAttributes.build()).isEqualTo(expected.build());

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, null, null);
    assertThat(endAttributes.build()).isEmpty();
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void extractsMethodWithoutClass() {
    Map<String, String> request = new HashMap<>();
    request.put("methodName", "doSomething");
    AttributesBuilder attributes = Attributes.builder();

    CodeAttributesExtractor.<Map<String, String>, Void>create(new TestAttributesGetter())
        .onStart(attributes, Context.root(), request);

    AttributesBuilder expected = Attributes.builder();
    if (emitOldCodeSemconv()) {
      expected.put(CODE_FUNCTION, "doSomething");
    }
    if (emitStableCodeSemconv()) {
      expected.put(CODE_FUNCTION_NAME, "doSomething");
    }
    assertThat(attributes.build()).isEqualTo(expected.build());
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void extractsClassWithoutMethod() {
    Map<String, String> request = new HashMap<>();
    request.put("class", "present");
    AttributesBuilder attributes = Attributes.builder();

    CodeAttributesExtractor.<Map<String, String>, Void>create(new TestAttributesGetter())
        .onStart(attributes, Context.root(), request);

    AttributesBuilder expected = Attributes.builder();
    if (emitOldCodeSemconv()) {
      expected.put(CODE_NAMESPACE, TestClass.class.getName());
    }
    if (emitStableCodeSemconv()) {
      expected.put(CODE_FUNCTION_NAME, TestClass.class.getName());
    }
    assertThat(attributes.build()).isEqualTo(expected.build());
  }

  @Test
  void extractsNothingWithoutCodeInformation() {
    AttributesBuilder attributes = Attributes.builder();

    CodeAttributesExtractor.<Map<String, String>, Void>create(new TestAttributesGetter())
        .onStart(attributes, Context.root(), emptyMap());

    assertThat(attributes.build()).isEmpty();
  }

  static class TestClass {}
}

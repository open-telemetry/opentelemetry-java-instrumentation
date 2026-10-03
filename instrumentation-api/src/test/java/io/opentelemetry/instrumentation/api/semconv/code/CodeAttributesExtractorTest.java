/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.code;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

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
      try {
        String className = request.get("class");
        return className == null ? null : Class.forName(className);
      } catch (ClassNotFoundException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public String getMethodName(Map<String, String> request) {
      return request.get("methodName");
    }
  }

  @Test
  void shouldExtractAllAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("class", TestClass.class.getName());
    request.put("methodName", "doSomething");

    Context context = Context.root();

    AttributesExtractor<Map<String, String>, Void> underTest =
        CodeAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, null, null);

    // then
    Attributes attributes = startAttributes.build();
    assertThat(attributes)
        .isEqualTo(Attributes.of(CODE_FUNCTION_NAME, TestClass.class.getName() + ".doSomething"));
    assertThat(endAttributes.build()).isEqualTo(Attributes.empty());
  }

  @Test
  void shouldExtractClassWithoutMethod() {
    AttributesExtractor<Map<String, String>, Void> underTest =
        CodeAttributesExtractor.create(new TestAttributesGetter());
    AttributesBuilder attributes = Attributes.builder();

    underTest.onStart(attributes, Context.root(), singletonMap("class", TestClass.class.getName()));

    assertThat(attributes.build())
        .isEqualTo(Attributes.of(CODE_FUNCTION_NAME, TestClass.class.getName()));
  }

  @Test
  void shouldExtractMethodWithoutClass() {
    AttributesExtractor<Map<String, String>, Void> underTest =
        CodeAttributesExtractor.create(new TestAttributesGetter());
    AttributesBuilder attributes = Attributes.builder();

    underTest.onStart(attributes, Context.root(), singletonMap("methodName", "doSomething"));

    assertThat(attributes.build()).isEqualTo(Attributes.of(CODE_FUNCTION_NAME, "doSomething"));
  }

  @Test
  void shouldOmitEmptyFunctionName() {
    AttributesExtractor<Map<String, String>, Void> underTest =
        CodeAttributesExtractor.create(new TestAttributesGetter());
    AttributesBuilder attributes = Attributes.builder();

    underTest.onStart(attributes, Context.root(), singletonMap("methodName", ""));

    assertThat(attributes.build()).isEqualTo(Attributes.empty());
  }

  @Test
  void shouldExtractNoAttributesIfNoneAreAvailable() {
    // given
    AttributesExtractor<Map<String, String>, Void> underTest =
        CodeAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Context.root(), emptyMap());

    // then
    assertThat(attributes.build()).isEqualTo(Attributes.empty());
  }

  static class TestClass {}
}

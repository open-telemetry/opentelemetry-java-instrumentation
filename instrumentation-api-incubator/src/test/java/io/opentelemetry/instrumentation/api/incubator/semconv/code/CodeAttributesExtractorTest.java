/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.code;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil;
import io.opentelemetry.semconv.CodeAttributes;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeAttributesExtractorTest {

  static final class TestAttributesGetter implements CodeAttributesGetter<Map<String, String>> {
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

  @SuppressWarnings("deprecation") // using deprecated semconv
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
    SemconvCodeStabilityUtil.codeFunctionAssertions(TestClass.class, "doSomething");

    if (SemconvStability.isEmitStableCodeSemconv()) {
      assertThat(attributes)
          .containsEntry(
              CodeAttributes.CODE_FUNCTION_NAME, TestClass.class.getName() + ".doSomething");
    }
    if (SemconvStability.isEmitOldCodeSemconv()) {
      assertThat(attributes)
          .containsEntry(CodeIncubatingAttributes.CODE_NAMESPACE, TestClass.class.getName())
          .containsEntry(CodeIncubatingAttributes.CODE_FUNCTION, "doSomething");
    }
    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldExtractNoAttributesIfNoneAreAvailable() {
    // given
    AttributesExtractor<Map<String, String>, Void> underTest =
        CodeAttributesExtractor.create(new TestAttributesGetter());

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Context.root(), Collections.emptyMap());

    // then
    assertThat(attributes.build().isEmpty()).isTrue();
  }

  static class TestClass {}
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.code;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeAttributesExtractorTest {

  static final class TestAtttributesGetter implements CodeAttributesGetter<Map<String, String>> {
    @Override
    public Class<?> codeClass(Map<String, String> request) {
      try {
        String className = request.get("class");
        return className == null ? null : Class.forName(className);
      } catch (ClassNotFoundException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public String methodName(Map<String, String> request) {
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

    CodeAttributesExtractor<Map<String, String>, Void> underTest =
        CodeAttributesExtractor.create(new TestAtttributesGetter());

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, null, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.CODE_NAMESPACE, TestClass.class.getName()),
            entry(SemanticAttributes.CODE_FUNCTION, "doSomething"));

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldExtractNoAttributesIfNoneAreAvailable() {
    // given
    CodeAttributesExtractor<Map<String, String>, Void> underTest =
        CodeAttributesExtractor.create(new TestAtttributesGetter());

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Context.root(), Collections.emptyMap());

    // then
    assertThat(attributes.build().isEmpty()).isTrue();
  }

  static class TestClass {}
}

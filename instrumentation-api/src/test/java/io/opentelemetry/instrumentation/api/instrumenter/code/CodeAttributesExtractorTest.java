/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.code;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeAttributesExtractorTest {

  static final CodeAttributesExtractor<Map<String, String>, Void> underTest =
      new CodeAttributesExtractor<Map<String, String>, Void>() {
        @Override
        protected Class<?> codeClass(Map<String, String> request) {
          try {
            String className = request.get("class");
            return className == null ? null : Class.forName(className);
          } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
          }
        }

        @Override
        protected String methodName(Map<String, String> request) {
          return request.get("methodName");
        }

        @Override
        protected String filePath(Map<String, String> request) {
          return request.get("filePath");
        }

        @Override
        protected Long lineNumber(Map<String, String> request) {
          String lineNo = request.get("lineNo");
          return lineNo == null ? null : Long.parseLong(lineNo);
        }
      };

  @Test
  void shouldExtractAllAttributes() {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("class", TestClass.class.getName());
    request.put("methodName", "doSomething");
    request.put("filePath", "/tmp/TestClass.java");
    request.put("lineNo", "42");

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, request, null, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.CODE_NAMESPACE, TestClass.class.getName()),
            entry(SemanticAttributes.CODE_FUNCTION, "doSomething"),
            entry(SemanticAttributes.CODE_FILEPATH, "/tmp/TestClass.java"),
            entry(SemanticAttributes.CODE_LINENO, 42L));

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldExtractNoAttributesIfNoneAreAvailable() {
    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Collections.emptyMap());

    // then
    assertThat(attributes.build().isEmpty()).isTrue();
  }

  static class TestClass {}
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.reflect.Method;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MethodSpanAttributesExtractorTest {

  Object request = new Object();
  Context context = Context.root();
  Method method = TestClass.getMethod();

  @Mock AttributesBuilder builder;
  @Mock Cache<Method, AttributeBindings> cache;

  @BeforeEach
  void setup() {
    lenient()
        .when(cache.computeIfAbsent(any(), any()))
        .thenAnswer(
            invocation -> {
              Method m = invocation.getArgument(0);
              Function<Method, AttributeBindings> fn = invocation.getArgument(1);
              return fn.apply(m);
            });
  }

  @Test
  void extractAttributesForMethodWithAttributeNames() {
    MethodSpanAttributesExtractor<Object, Object> extractor =
        new MethodSpanAttributesExtractor<>(
            r -> method,
            (m, p) -> new String[] {"x", "y", "z"},
            r -> new String[] {"a", "b", "c"},
            cache);

    extractor.onStart(builder, context, request);

    verify(builder).put(stringKey("x"), "a");
    verify(builder).put(stringKey("y"), "b");
    verify(builder).put(stringKey("z"), "c");
  }

  @Test
  void doesNotExtractAttributesForEmptyAttributeNameArray() {
    MethodSpanAttributesExtractor<Object, Object> extractor =
        new MethodSpanAttributesExtractor<>(
            r -> method, (m, p) -> new String[0], r -> new String[] {"a", "b", "c"}, cache);

    extractor.onStart(builder, context, request);
    verifyNoInteractions(builder);
  }

  @Test
  void doesNotExtractAttributesForMethodWithAttributeNamesArrayWithFewerElementsThanParams() {
    MethodSpanAttributesExtractor<Object, Object> extractor =
        new MethodSpanAttributesExtractor<>(
            r -> method,
            (m, p) -> new String[] {"x", "y"},
            r -> new String[] {"a", "b", "c"},
            cache);

    extractor.onStart(builder, context, request);
    verifyNoInteractions(builder);
  }

  @Test
  void extractsAttributesForMethodWithAttributeNamesArrayWithNullElement() {
    MethodSpanAttributesExtractor<Object, Object> extractor =
        new MethodSpanAttributesExtractor<>(
            r -> method,
            (m, p) -> new String[] {"x", null, "z"},
            r -> new String[] {"a", "b", "c"},
            cache);

    extractor.onStart(builder, context, request);

    verify(builder).put(stringKey("x"), "a");
    verify(builder).put(stringKey("z"), "c");
    verifyNoMoreInteractions(builder);
  }

  @Test
  void doesNotExtractAttributeForMethodWithNullArgument() {
    MethodSpanAttributesExtractor<Object, Object> extractor =
        new MethodSpanAttributesExtractor<>(
            r -> method,
            (m, p) -> new String[] {"x", "y", "z"},
            r -> new String[] {"a", "b", null},
            cache);

    extractor.onStart(builder, context, request);

    verify(builder).put(stringKey("x"), "a");
    verify(builder).put(stringKey("y"), "b");
    verifyNoMoreInteractions(builder);
  }

  @Test
  @SuppressWarnings("MockitoDoSetup")
  void appliesCachedBindings() {
    AttributeBindings bindings = mock(AttributeBindings.class);
    when(bindings.isEmpty()).thenReturn(false);
    doAnswer(invocation -> bindings).when(cache).computeIfAbsent(any(), any());

    MethodSpanAttributesExtractor<Object, Object> extractor =
        new MethodSpanAttributesExtractor<>(
            r -> method,
            (m, p) -> {
              throw new RuntimeException();
            },
            r -> new String[] {"a", "b", "c"},
            cache);

    extractor.onStart(builder, context, request);

    verify(bindings).apply(builder, new Object[] {"a", "b", "c"});
  }

  @Test
  @SuppressWarnings("MockitoDoSetup")
  void doesNotApplyCachedEmptyBindings() {
    AttributeBindings bindings = mock(AttributeBindings.class);
    when(bindings.isEmpty()).thenReturn(true);
    doAnswer(invocation -> bindings).when(cache).computeIfAbsent(any(), any());

    MethodSpanAttributesExtractor<Object, Object> extractor =
        new MethodSpanAttributesExtractor<>(
            r -> method,
            (m, p) -> {
              throw new RuntimeException();
            },
            r -> {
              throw new RuntimeException();
            },
            cache);

    extractor.onStart(builder, context, request);

    verify(bindings, never()).apply(isA(AttributesBuilder.class), isA(Object[].class));
  }

  static class TestClass {
    public static Method getMethod() {
      try {
        return TestClass.class.getDeclaredMethod(
            "method", String.class, String.class, String.class);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    @SuppressWarnings("unused")
    void method(String x, String y, String z) {}
  }
}

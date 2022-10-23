/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.javaagent.instrumentation.jaxrs.HandlerData;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;

public class Jaxrs2HandlerData extends HandlerData {

  private static final ClassValue<Map<Method, String>> serverSpanNames =
      new ClassValue<Map<Method, String>>() {
        @Override
        protected Map<Method, String> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  public Jaxrs2HandlerData(Class<?> target, Method method) {
    super(target, method);
  }

  /**
   * Returns the span name given a JaxRS annotated method. Results are cached so this method can be
   * called multiple times without significantly impacting performance.
   *
   * @return The result can be an empty string but will never be {@code null}.
   */
  @Override
  public String getServerSpanName() {
    Map<Method, String> classMap = serverSpanNames.get(target);
    String spanName = classMap.get(method);
    if (spanName == null) {
      spanName = super.getServerSpanName();
      classMap.put(method, spanName);
    }

    return spanName;
  }

  @Override
  protected Class<? extends Annotation> getHttpMethodAnnotation() {
    return HttpMethod.class;
  }

  @Override
  protected Supplier<String> getPathAnnotation(AnnotatedElement annotatedElement) {
    Path path = annotatedElement.getAnnotation(Path.class);
    return path != null ? path::value : null;
  }
}

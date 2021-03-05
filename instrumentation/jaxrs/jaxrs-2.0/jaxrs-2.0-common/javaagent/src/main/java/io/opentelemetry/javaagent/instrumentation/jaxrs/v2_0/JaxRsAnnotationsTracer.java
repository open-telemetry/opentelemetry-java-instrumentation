/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.tooling.ClassHierarchyIterable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;

public class JaxRsAnnotationsTracer extends BaseTracer {
  public static final String ABORT_FILTER_CLASS =
      "io.opentelemetry.javaagent.instrumentation.jaxrs2.filter.abort.class";
  public static final String ABORT_HANDLED =
      "io.opentelemetry.javaagent.instrumentation.jaxrs2.filter.abort.handled";

  private static final JaxRsAnnotationsTracer TRACER = new JaxRsAnnotationsTracer();

  public static JaxRsAnnotationsTracer tracer() {
    return TRACER;
  }

  private final ClassValue<Map<Method, String>> spanNames =
      new ClassValue<Map<Method, String>>() {
        @Override
        protected Map<Method, String> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  public Context startSpan(Class<?> target, Method method) {
    return startSpan(Context.current(), target, method);
  }

  public Context startSpan(Context parentContext, Class<?> target, Method method) {
    // We create span and immediately update its name
    // We do that in order to reuse logic inside updateSpanNames method, which is used externally as
    // well.
    Span span = tracer.spanBuilder("jax-rs.request").setParent(parentContext).startSpan();
    updateSpanNames(
        parentContext, span, ServerSpan.fromContextOrNull(parentContext), target, method);
    return parentContext.with(span);
  }

  public void updateSpanNames(
      Context context, Span span, Span serverSpan, Class<?> target, Method method) {
    String pathBasedSpanName = ServletContextPath.prepend(context, getPathSpanName(target, method));
    if (serverSpan == null) {
      updateSpanName(span, pathBasedSpanName);
    } else {
      updateSpanName(serverSpan, pathBasedSpanName);
      updateSpanName(span, tracer().spanNameForMethod(target, method));
    }
  }

  private void updateSpanName(Span span, String spanName) {
    if (!spanName.isEmpty()) {
      span.updateName(spanName);
    }
  }

  /**
   * Returns the span name given a JaxRS annotated method. Results are cached so this method can be
   * called multiple times without significantly impacting performance.
   *
   * @return The result can be an empty string but will never be {@code null}.
   */
  private String getPathSpanName(Class<?> target, Method method) {
    Map<Method, String> classMap = spanNames.get(target);
    String spanName = classMap.get(method);
    if (spanName == null) {
      String httpMethod = null;
      Path methodPath = null;
      Path classPath = findClassPath(target);
      for (Class<?> currentClass : new ClassHierarchyIterable(target)) {
        Method currentMethod;
        if (currentClass.equals(target)) {
          currentMethod = method;
        } else {
          currentMethod = findMatchingMethod(method, currentClass.getDeclaredMethods());
        }

        if (currentMethod != null) {
          if (httpMethod == null) {
            httpMethod = locateHttpMethod(currentMethod);
          }
          if (methodPath == null) {
            methodPath = findMethodPath(currentMethod);
          }

          if (httpMethod != null && methodPath != null) {
            break;
          }
        }
      }
      spanName = buildSpanName(classPath, methodPath);
      classMap.put(method, spanName);
    }

    return spanName;
  }

  private String locateHttpMethod(Method method) {
    String httpMethod = null;
    for (Annotation ann : method.getDeclaredAnnotations()) {
      if (ann.annotationType().getAnnotation(HttpMethod.class) != null) {
        httpMethod = ann.annotationType().getSimpleName();
      }
    }
    return httpMethod;
  }

  private Path findMethodPath(Method method) {
    return method.getAnnotation(Path.class);
  }

  private Path findClassPath(Class<?> target) {
    for (Class<?> currentClass : new ClassHierarchyIterable(target)) {
      Path annotation = currentClass.getAnnotation(Path.class);
      if (annotation != null) {
        // Annotation overridden, no need to continue.
        return annotation;
      }
    }

    return null;
  }

  private Method findMatchingMethod(Method baseMethod, Method[] methods) {
    nextMethod:
    for (Method method : methods) {
      if (!baseMethod.getReturnType().equals(method.getReturnType())) {
        continue;
      }

      if (!baseMethod.getName().equals(method.getName())) {
        continue;
      }

      Class<?>[] baseParameterTypes = baseMethod.getParameterTypes();
      Class<?>[] parameterTypes = method.getParameterTypes();
      if (baseParameterTypes.length != parameterTypes.length) {
        continue;
      }
      for (int i = 0; i < baseParameterTypes.length; i++) {
        if (!baseParameterTypes[i].equals(parameterTypes[i])) {
          continue nextMethod;
        }
      }
      return method;
    }
    return null;
  }

  private String buildSpanName(Path classPath, Path methodPath) {
    String spanName;
    StringBuilder spanNameBuilder = new StringBuilder();
    boolean skipSlash = false;
    if (classPath != null) {
      String classPathValue = classPath.value();
      if (!classPathValue.startsWith("/")) {
        spanNameBuilder.append("/");
      }
      spanNameBuilder.append(classPathValue);
      skipSlash = classPathValue.endsWith("/") || classPathValue.isEmpty();
    }

    if (methodPath != null) {
      String path = methodPath.value();
      if (skipSlash) {
        if (path.startsWith("/")) {
          path = path.length() == 1 ? "" : path.substring(1);
        }
      } else if (!path.startsWith("/")) {
        spanNameBuilder.append("/");
      }
      spanNameBuilder.append(path);
    }

    spanName = spanNameBuilder.toString().trim();

    return spanName;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jaxrs-2.0-common";
  }
}

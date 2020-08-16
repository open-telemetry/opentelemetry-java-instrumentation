/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.jaxrs.v2_0;

import static io.opentelemetry.instrumentation.auto.api.WeakMap.Provider.newWeakMap;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.auto.api.WeakMap;
import io.opentelemetry.javaagent.tooling.ClassHierarchyIterable;
import io.opentelemetry.trace.Span;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;

public class JaxRsAnnotationsTracer extends BaseTracer {
  public static final String ABORT_FILTER_CLASS =
      "io.opentelemetry.instrumentation.auto.jaxrs2.filter.abort.class";
  public static final String ABORT_HANDLED =
      "io.opentelemetry.instrumentation.auto.jaxrs2.filter.abort.handled";

  public static final JaxRsAnnotationsTracer TRACER = new JaxRsAnnotationsTracer();

  private final WeakMap<Class<?>, Map<Method, String>> spanNames = newWeakMap();

  public Span startSpan(Class<?> target, Method method) {
    // We create span and immediately update its name
    // We do that in order to reuse logic inside updateSpanNames method, which is used externally as
    // well.
    Span span = tracer.spanBuilder("jax-rs.request").startSpan();
    updateSpanNames(span, getCurrentServerSpan(), target, method);
    return span;
  }

  public void updateSpanNames(
      Span span, Span serverSpan, Class<?> target, Method method) {
    // When jax-rs is the root, we want to name using the path, otherwise use the class/method.
    String pathBasedSpanName = getPathSpanName(target, method);
    if (serverSpan == null) {
      updateSpanName(span, pathBasedSpanName);
    } else {
      updateSpanName(serverSpan, pathBasedSpanName);
      updateSpanName(span, TRACER.spanNameForMethod(target, method));
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

    if (classMap == null) {
      spanNames.putIfAbsent(target, new ConcurrentHashMap<Method, String>());
      classMap = spanNames.get(target);
      // classMap should not be null at this point because we have a
      // strong reference to target and don't manually clear the map.
    }

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
      spanName = buildSpanName(httpMethod, classPath, methodPath);
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

  private String buildSpanName(
      String httpMethod, Path classPath, Path methodPath) {
    String spanName;
    StringBuilder spanNameBuilder = new StringBuilder();
    if (httpMethod != null) {
      spanNameBuilder.append(httpMethod);
      spanNameBuilder.append(" ");
    }
    boolean skipSlash = false;
    if (classPath != null) {
      if (!classPath.value().startsWith("/")) {
        spanNameBuilder.append("/");
      }
      spanNameBuilder.append(classPath.value());
      skipSlash = classPath.value().endsWith("/");
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
    return "io.opentelemetry.auto.jaxrs-2.0";
  }
}

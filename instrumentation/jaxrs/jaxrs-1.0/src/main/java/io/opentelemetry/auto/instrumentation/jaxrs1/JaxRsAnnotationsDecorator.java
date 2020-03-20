/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.jaxrs1;

import static io.opentelemetry.auto.bootstrap.WeakMap.Provider.newWeakMap;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.WeakMap;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.auto.tooling.ClassHierarchyIterable;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;

public class JaxRsAnnotationsDecorator extends BaseDecorator {
  public static final JaxRsAnnotationsDecorator DECORATE = new JaxRsAnnotationsDecorator();

  private final WeakMap<Class, Map<Method, String>> resourceNames = newWeakMap();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.jaxrs-1.0");

  @Override
  protected String getComponentName() {
    return "jax-rs-controller";
  }

  public void onControllerStart(
      final Span span, final Span parent, final Class target, final Method method) {
    final String resourceName = getPathResourceName(target, method);
    updateParent(parent, resourceName);

    // When jax-rs is the root, we want to name using the path, otherwise use the class/method.
    final boolean isRootScope = !parent.getContext().isValid();
    if (isRootScope && !resourceName.isEmpty()) {
      span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);
    } else {
      span.setAttribute(
          MoreTags.RESOURCE_NAME, DECORATE.spanNameForClass(target) + "." + method.getName());
    }
  }

  private void updateParent(final Span span, final String resourceName) {
    if (span == null) {
      return;
    }
    span.setAttribute(Tags.COMPONENT, "jax-rs");

    if (!resourceName.isEmpty()) {
      span.updateName(resourceName);
      span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);
    }
  }

  /**
   * Returns the resource name given a JaxRS annotated method. Results are cached so this method can
   * be called multiple times without significantly impacting performance.
   *
   * @return The result can be an empty string but will never be {@code null}.
   */
  private String getPathResourceName(final Class target, final Method method) {
    Map<Method, String> classMap = resourceNames.get(target);

    if (classMap == null) {
      resourceNames.putIfAbsent(target, new ConcurrentHashMap<Method, String>());
      classMap = resourceNames.get(target);
      // classMap should not be null at this point because we have a
      // strong reference to target and don't manually clear the map.
    }

    String resourceName = classMap.get(method);
    if (resourceName == null) {
      String httpMethod = null;
      Path methodPath = null;
      final Path classPath = findClassPath(target);
      for (final Class currentClass : new ClassHierarchyIterable(target)) {
        final Method currentMethod;
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
      resourceName = buildResourceName(httpMethod, classPath, methodPath);
      classMap.put(method, resourceName);
    }

    return resourceName;
  }

  private String locateHttpMethod(final Method method) {
    String httpMethod = null;
    for (final Annotation ann : method.getDeclaredAnnotations()) {
      if (ann.annotationType().getAnnotation(HttpMethod.class) != null) {
        httpMethod = ann.annotationType().getSimpleName();
      }
    }
    return httpMethod;
  }

  private Path findMethodPath(final Method method) {
    return method.getAnnotation(Path.class);
  }

  private Path findClassPath(final Class<Object> target) {
    for (final Class<?> currentClass : new ClassHierarchyIterable(target)) {
      final Path annotation = currentClass.getAnnotation(Path.class);
      if (annotation != null) {
        // Annotation overridden, no need to continue.
        return annotation;
      }
    }

    return null;
  }

  private Method findMatchingMethod(final Method baseMethod, final Method[] methods) {
    nextMethod:
    for (final Method method : methods) {
      if (!baseMethod.getReturnType().equals(method.getReturnType())) {
        continue;
      }

      if (!baseMethod.getName().equals(method.getName())) {
        continue;
      }

      final Class<?>[] baseParameterTypes = baseMethod.getParameterTypes();
      final Class<?>[] parameterTypes = method.getParameterTypes();
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

  private String buildResourceName(
      final String httpMethod, final Path classPath, final Path methodPath) {
    final String resourceName;
    final StringBuilder resourceNameBuilder = new StringBuilder();
    if (httpMethod != null) {
      resourceNameBuilder.append(httpMethod);
      resourceNameBuilder.append(" ");
    }
    boolean skipSlash = false;
    if (classPath != null) {
      if (!classPath.value().startsWith("/")) {
        resourceNameBuilder.append("/");
      }
      resourceNameBuilder.append(classPath.value());
      skipSlash = classPath.value().endsWith("/");
    }

    if (methodPath != null) {
      String path = methodPath.value();
      if (skipSlash) {
        if (path.startsWith("/")) {
          path = path.length() == 1 ? "" : path.substring(1);
        }
      } else if (!path.startsWith("/")) {
        resourceNameBuilder.append("/");
      }
      resourceNameBuilder.append(path);
    }

    resourceName = resourceNameBuilder.toString().trim();
    return resourceName;
  }
}

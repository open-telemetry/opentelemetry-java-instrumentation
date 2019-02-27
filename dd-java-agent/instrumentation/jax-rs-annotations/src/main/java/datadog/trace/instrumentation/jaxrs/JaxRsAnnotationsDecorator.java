package datadog.trace.instrumentation.jaxrs;

import static datadog.trace.bootstrap.WeakMap.Provider.newWeakMap;

import datadog.trace.agent.decorator.BaseDecorator;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.WeakMap;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;

public class JaxRsAnnotationsDecorator extends BaseDecorator {
  public static JaxRsAnnotationsDecorator DECORATE = new JaxRsAnnotationsDecorator();

  private final WeakMap<Class, Map<String, String>> resourceNames = newWeakMap();

  @Override
  protected String[] instrumentationNames() {
    return new String[0];
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "jax-rs-controller";
  }

  public void updateParent(final Scope scope, final Method method) {
    if (scope == null) {
      return;
    }
    final Span span = scope.span();
    Tags.COMPONENT.set(span, "jax-rs");

    Class<?> target = method.getDeclaringClass();
    Map<String, String> classMap = resourceNames.get(target);

    if (classMap == null) {
      resourceNames.putIfAbsent(target, new ConcurrentHashMap<String, String>());
      classMap = resourceNames.get(target);
    }

    final String methodName = method.toString();
    String resourceName = classMap.get(methodName);
    if (resourceName == null) {
      final LinkedList<Path> paths = new LinkedList<>();
      while (target != Object.class) {
        final Path annotation = target.getAnnotation(Path.class);
        if (annotation != null) {
          paths.push(annotation);
        }
        target = target.getSuperclass();
      }
      final Path methodPath = method.getAnnotation(Path.class);
      if (methodPath != null) {
        paths.add(methodPath);
      }
      String httpMethod = null;
      for (final Annotation ann : method.getDeclaredAnnotations()) {
        if (ann.annotationType().getAnnotation(HttpMethod.class) != null) {
          httpMethod = ann.annotationType().getSimpleName();
        }
      }

      final StringBuilder resourceNameBuilder = new StringBuilder();
      if (httpMethod != null) {
        resourceNameBuilder.append(httpMethod);
        resourceNameBuilder.append(" ");
      }
      Path last = null;
      for (final Path path : paths) {
        if (path.value().startsWith("/") || (last != null && last.value().endsWith("/"))) {
          resourceNameBuilder.append(path.value());
        } else {
          resourceNameBuilder.append("/");
          resourceNameBuilder.append(path.value());
        }
        last = path;
      }
      resourceName = resourceNameBuilder.toString().trim();
      classMap.put(methodName, resourceName);
    }

    if (!resourceName.isEmpty()) {
      span.setTag(DDTags.RESOURCE_NAME, resourceName);
    }
  }
}

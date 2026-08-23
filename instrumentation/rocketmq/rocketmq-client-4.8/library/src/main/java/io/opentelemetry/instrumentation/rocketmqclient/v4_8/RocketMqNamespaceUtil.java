/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import java.lang.reflect.Method;
import javax.annotation.Nullable;

final class RocketMqNamespaceUtil {

  private static final NamespaceAccessor noNamespaceAccessor = context -> null;
  private static final ClassValue<NamespaceAccessor> namespaceAccessors =
      new ClassValue<NamespaceAccessor>() {
        @Override
        protected NamespaceAccessor computeValue(Class<?> type) {
          try {
            Method method = type.getMethod("getNamespace");
            return context -> invoke(method, context);
          } catch (NoSuchMethodException ignored) {
            return noNamespaceAccessor;
          }
        }
      };

  @Nullable
  static String getNamespace(Object context) {
    return namespaceAccessors.get(context.getClass()).get(context);
  }

  @Nullable
  private static String invoke(Method method, Object context) {
    try {
      return (String) method.invoke(context);
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  static String withoutNamespace(String resource, @Nullable String namespace) {
    if (resource.isEmpty() || namespace == null || namespace.isEmpty()) {
      return resource;
    }

    String prefix = "";
    String resourceWithoutPrefix = resource;
    if (resource.startsWith("%RETRY%")) {
      prefix = "%RETRY%";
      resourceWithoutPrefix = resource.substring(prefix.length());
    } else if (resource.startsWith("%DLQ%")) {
      prefix = "%DLQ%";
      resourceWithoutPrefix = resource.substring(prefix.length());
    }

    String namespacePrefix = namespace + "%";
    return resourceWithoutPrefix.startsWith(namespacePrefix)
        ? prefix + resourceWithoutPrefix.substring(namespacePrefix.length())
        : resource;
  }

  private RocketMqNamespaceUtil() {}

  private interface NamespaceAccessor {
    @Nullable
    String get(Object context);
  }
}

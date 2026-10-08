/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import java.lang.reflect.Method;
import javax.annotation.Nullable;

/**
 * Resolves the Elasticsearch wire action name, such as {@code indices:data/read/search}, of a
 * transport action, or returns {@code null} when a valid wire name is unavailable.
 *
 * <p>The {@code name()} accessor is declared on three unrelated types over the supported version
 * range: {@code org.elasticsearch.action.GenericAction} up to 6.5, {@code
 * org.elasticsearch.action.Action} in 7.0 and 7.2, and {@code org.elasticsearch.action.ActionType}
 * from 7.3 on. Each release removed its predecessor, so there is no type to compile against and the
 * lookup is reflective.
 */
final class ElasticsearchActionNames {

  private static final ClassValue<Method> nameMethod =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> type) {
          try {
            return type.getMethod("name");
          } catch (NoSuchMethodException ignored) {
            return null;
          }
        }
      };

  @Nullable
  static String wireName(Object action) {
    Method method = nameMethod.get(action.getClass());
    if (method != null) {
      try {
        Object name = method.invoke(action);
        if (name instanceof String && !((String) name).isEmpty()) {
          return (String) name;
        }
      } catch (ReflectiveOperationException ignored) {
        // fall through
      }
    }
    return null;
  }

  private ElasticsearchActionNames() {}
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.lang.reflect.Method;

/**
 * Resolves the Elasticsearch wire action name, such as {@code indices:data/read/search}, of a
 * transport action.
 *
 * <p>The {@code name()} accessor is declared on three unrelated types over the supported version
 * range: {@code org.elasticsearch.action.GenericAction} up to 6.5, {@code
 * org.elasticsearch.action.Action} in 7.0 and 7.2, and {@code org.elasticsearch.action.ActionType}
 * from 7.3 on. Each release removed its predecessor, so there is no type to compile against and the
 * lookup is reflective. Action instances are effectively singletons, so caching the resolved name
 * per action class keeps the cache small and the reflective lookup to once per class.
 */
final class ElasticsearchActionNames {

  private static final Cache<Class<?>, String> namesByActionClass = Cache.weak();

  static String wireName(Object action) {
    Class<?> actionClass = action.getClass();
    String name = namesByActionClass.get(actionClass);
    if (name == null) {
      name = resolve(action, actionClass);
      namesByActionClass.put(actionClass, name);
    }
    return name;
  }

  private static String resolve(Object action, Class<?> actionClass) {
    try {
      Method method = actionClass.getMethod("name");
      if (String.class.equals(method.getReturnType())) {
        String name = (String) method.invoke(action);
        if (name != null && !name.isEmpty()) {
          return name;
        }
      }
    } catch (Throwable t) {
      // fall through to the class name
    }
    return actionClass.getSimpleName();
  }

  private ElasticsearchActionNames() {}
}

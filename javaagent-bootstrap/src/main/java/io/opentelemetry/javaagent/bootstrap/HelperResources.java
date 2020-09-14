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

package io.opentelemetry.javaagent.bootstrap;

import static io.opentelemetry.instrumentation.auto.api.WeakMap.Provider.newWeakMap;

import io.opentelemetry.instrumentation.auto.api.WeakMap;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A holder of resources needed by instrumentation. We store them in the bootstrap classloader so
 * instrumentation can store from the agent classloader and apps can retrieve from the app
 * classloader.
 */
public final class HelperResources {

  private static final WeakMap<ClassLoader, Map<String, URL>> RESOURCES = newWeakMap();

  /** Registers the {@code payload} to be available to instrumentation at {@code path}. */
  public static void register(ClassLoader classLoader, String path, URL url) {
    RESOURCES.putIfAbsent(classLoader, new ConcurrentHashMap<String, URL>());
    RESOURCES.get(classLoader).put(path, url);
  }

  /**
   * Returns a {@link URL} that can be used to retrieve the content of the resource at {@code path},
   * or {@code null} if no resource could be found at {@code path}.
   */
  public static URL load(ClassLoader classLoader, String path) {
    Map<String, URL> map = RESOURCES.get(classLoader);
    if (map == null) {
      return null;
    }

    return map.get(path);
  }

  private HelperResources() {}
}

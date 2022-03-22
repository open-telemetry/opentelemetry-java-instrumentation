/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import io.opentelemetry.instrumentation.api.cache.Cache;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A holder of resources needed by instrumentation. We store them in the bootstrap classloader so
 * instrumentation can store from the agent classloader and apps can retrieve from the app
 * classloader.
 */
public final class HelperResources {

  private static final Cache<ClassLoader, Map<String, URL>> RESOURCES = Cache.weak();
  private static final Map<String, URL> ALL_CLASSLOADERS_RESOURCES = new ConcurrentHashMap<>();

  /**
   * Registers the {@code url} to be available to instrumentation at {@code path}, when given {@code
   * classLoader} attempts to load that resource.
   */
  public static void register(ClassLoader classLoader, String path, URL url) {
    RESOURCES.computeIfAbsent(classLoader, unused -> new ConcurrentHashMap<>()).put(path, url);
  }

  /** Registers the {@code url} to be available to instrumentation at {@code path}. */
  public static void registerForAllClassloaders(String path, URL url) {
    ALL_CLASSLOADERS_RESOURCES.putIfAbsent(path, url);
  }

  /**
   * Returns a {@link URL} that can be used to retrieve the content of the resource at {@code path},
   * or {@code null} if no resource could be found at {@code path}.
   */
  public static URL load(ClassLoader classLoader, String path) {
    Map<String, URL> map = RESOURCES.get(classLoader);
    URL resource = null;
    if (map != null) {
      resource = map.get(path);
    }
    if (resource == null) {
      resource = ALL_CLASSLOADERS_RESOURCES.get(path);
    }
    return resource;
  }

  private HelperResources() {}
}

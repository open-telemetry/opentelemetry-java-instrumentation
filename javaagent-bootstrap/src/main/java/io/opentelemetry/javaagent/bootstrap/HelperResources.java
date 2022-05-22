/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * A holder of resources needed by instrumentation. We store them in the bootstrap classloader so
 * instrumentation can store from the agent classloader and apps can retrieve from the app
 * classloader.
 */
public final class HelperResources {

  private static final Cache<ClassLoader, Map<String, List<URL>>> RESOURCES = Cache.weak();
  private static final Map<String, List<URL>> ALL_CLASSLOADERS_RESOURCES =
      new ConcurrentHashMap<>();

  /**
   * Registers the {@code urls} to be available to instrumentation at {@code path}, when given
   * {@code classLoader} attempts to load that resource.
   */
  public static void register(ClassLoader classLoader, String path, List<URL> urls) {
    RESOURCES
        .computeIfAbsent(classLoader, unused -> new ConcurrentHashMap<>())
        .compute(path, (k, v) -> append(v, urls));
  }

  /** Registers the {@code urls} to be available to instrumentation at {@code path}. */
  public static void registerForAllClassLoaders(String path, List<URL> urls) {
    ALL_CLASSLOADERS_RESOURCES.compute(path, (k, v) -> append(v, urls));
  }

  private static List<URL> append(@Nullable List<URL> resources, List<URL> toAdd) {
    List<URL> newResources = resources == null ? new ArrayList<>() : new ArrayList<>(resources);
    for (URL newResource : toAdd) {
      // make sure to de-dupe resources - each extension classloader has the agent classloader as
      // its parent, and the MultipleParentClassLoader (that every individual extension CL gets put
      // into) concatenates all found resources on getResources(); this means that if you ask for a
      // built-in agent resource, each extension CL will also return URL pointing to it, thus the
      // final collection will have (no of extension CLs) + 1 duplicates of the same URL
      if (!containsSameFile(newResources, newResource)) {
        newResources.add(newResource);
      }
    }
    return unmodifiableList(newResources);
  }

  private static boolean containsSameFile(List<URL> haystack, URL needle) {
    for (URL r : haystack) {
      if (r.sameFile(needle)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a {@link URL} that can be used to retrieve the content of the resource at {@code path},
   * or {@code null} if no resource could be found at {@code path}.
   */
  public static URL loadOne(ClassLoader classLoader, String path) {
    List<URL> resources = loadAll(classLoader, path);
    return resources.isEmpty() ? null : resources.get(0);
  }

  /**
   * Returns all {@link URL}s that can be used to retrieve the content of the resource at {@code
   * path}.
   */
  public static List<URL> loadAll(ClassLoader classLoader, String path) {
    Map<String, List<URL>> map = RESOURCES.get(classLoader);
    List<URL> resources = null;
    if (map != null) {
      resources = map.get(path);
    }
    if (resources == null) {
      resources = ALL_CLASSLOADERS_RESOURCES.get(path);
    }
    return resources == null ? emptyList() : resources;
  }

  private HelperResources() {}
}

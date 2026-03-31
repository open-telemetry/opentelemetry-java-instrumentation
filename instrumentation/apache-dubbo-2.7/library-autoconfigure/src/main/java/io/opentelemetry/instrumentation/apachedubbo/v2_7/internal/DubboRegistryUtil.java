/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.Directory;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboRegistryUtil {

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  private static final Map<Class<?>, Optional<MethodHandle>> DIRECTORY_ACCESSOR_CACHE =
      new ConcurrentHashMap<>();
  private static final Map<Class<?>, Optional<MethodHandle>> REGISTRY_ACCESSOR_CACHE =
      new ConcurrentHashMap<>();
  private static final Map<Class<?>, Optional<MethodHandle>> URL_ACCESSOR_CACHE =
      new ConcurrentHashMap<>();

  private static final ThreadLocal<String> CAPTURED_REGISTRY_ADDRESS = new ThreadLocal<>();

  /**
   * Called by RegistryAddressCaptureFilter (order -10001) before ConsumerContextFilter overwrites
   * invocation.getInvoker(). In Dubbo 2.7.5+ and 3.x the invoker at this point is the cluster
   * invoker with getDirectory().
   */
  public static void captureRegistryAddress(RpcInvocation invocation) {
    Invoker<?> invoker = invocation.getInvoker();
    if (invoker == null) {
      return;
    }
    Directory<?> directory = getDirectory(invoker);
    if (directory != null) {
      String address = extractRegistryAddressFromDirectory(directory);
      if (address != null) {
        CAPTURED_REGISTRY_ADDRESS.set(address);
      }
    }
  }

  public static void clearCapturedRegistryAddress() {
    CAPTURED_REGISTRY_ADDRESS.remove();
  }

  @Nullable
  public static String extractRegistryAddress(RpcInvocation invocation) {
    // 1. Check ThreadLocal (set by RegistryAddressCaptureFilter for Dubbo 2.7.5+ and 3.x)
    String captured = CAPTURED_REGISTRY_ADDRESS.get();
    if (captured != null) {
      return captured;
    }

    Invoker<?> invoker = invocation.getInvoker();
    if (invoker == null) {
      return null;
    }

    // 2. Try invoker reflection (Dubbo 3.x fallback if capture filter didn't run)
    Directory<?> directory = getDirectory(invoker);
    if (directory != null) {
      String address = extractRegistryAddressFromDirectory(directory);
      if (address != null) {
        return address;
      }
    }

    // 3. Try ProviderConsumerRegTable (Dubbo 2.7.0-2.7.4)
    return RegTableLookup.extractFromRegTable(invoker.getUrl());
  }

  public static String buildServiceTarget(URL url) {
    String interfaceName = url.getServiceInterface();
    if (interfaceName == null || interfaceName.isEmpty()) {
      interfaceName = url.getPath();
    }
    if (interfaceName == null || interfaceName.isEmpty()) {
      return "";
    }

    String version = url.getParameter("version");
    String group = url.getParameter("group");
    boolean hasVersion = version != null && !version.isEmpty();
    boolean hasGroup = group != null && !group.isEmpty();

    if (!hasVersion && !hasGroup) {
      return interfaceName;
    }

    StringBuilder sb = new StringBuilder(interfaceName);
    sb.append(':');
    if (hasVersion) {
      sb.append(version);
    }
    if (hasGroup) {
      sb.append(':').append(group);
    }
    return sb.toString();
  }

  @Nullable
  private static Directory<?> getDirectory(Invoker<?> invoker) {
    MethodHandle mh =
        findAccessor(invoker.getClass(), "getDirectory", "directory", DIRECTORY_ACCESSOR_CACHE);
    if (mh == null) {
      return null;
    }
    try {
      Object obj = mh.invoke(invoker);
      return obj instanceof Directory ? (Directory<?>) obj : null;
    } catch (Throwable t) {
      return null;
    }
  }

  @Nullable
  private static String extractRegistryAddressFromDirectory(Directory<?> directory) {
    MethodHandle getRegistry =
        findAccessor(directory.getClass(), "getRegistry", "registry", REGISTRY_ACCESSOR_CACHE);
    if (getRegistry == null) {
      return null;
    }
    try {
      Object registry = getRegistry.invoke(directory);
      if (registry == null) {
        return null;
      }
      MethodHandle getUrl =
          findAccessor(registry.getClass(), "getUrl", null, URL_ACCESSOR_CACHE);
      if (getUrl == null) {
        return null;
      }
      Object urlObj = getUrl.invoke(registry);
      if (!(urlObj instanceof URL)) {
        return null;
      }
      URL url = (URL) urlObj;
      return url.getProtocol() + "://" + url.getAddress();
    } catch (Throwable t) {
      return null;
    }
  }

  @Nullable
  private static MethodHandle findAccessor(
      Class<?> clazz,
      String methodName,
      @Nullable String fieldName,
      Map<Class<?>, Optional<MethodHandle>> cache) {
    return cache
        .computeIfAbsent(
            clazz,
            cls -> {
              MethodHandle mh = resolveMethod(cls, methodName);
              if (mh != null) {
                return Optional.of(mh);
              }
              if (fieldName != null) {
                mh = resolveField(cls, fieldName);
                if (mh != null) {
                  return Optional.of(mh);
                }
              }
              return Optional.empty();
            })
        .orElse(null);
  }

  @Nullable
  private static MethodHandle resolveMethod(Class<?> clazz, String name) {
    try {
      Method m = clazz.getMethod(name);
      m.setAccessible(true);
      return LOOKUP.unreflect(m);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }

  @Nullable
  private static MethodHandle resolveField(Class<?> clazz, String name) {
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      try {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return LOOKUP.unreflectGetter(f);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        // continue up the hierarchy
      }
    }
    return null;
  }

  /**
   * Lazily-initialized holder for ProviderConsumerRegTable access. This class only exists in Dubbo
   * 2.7.0-2.7.4; all reflection is wrapped to fail gracefully on Dubbo 2.7.5+ and 3.x.
   */
  private static class RegTableLookup {
    private static final MethodHandle CONSUMER_INVOKERS_GETTER;
    private static final MethodHandle GET_REGISTRY_URL;

    static {
      MethodHandle consumerInvokersGetter = null;
      MethodHandle getRegistryUrl = null;
      try {
        Class<?> tableClass =
            Class.forName("org.apache.dubbo.registry.support.ProviderConsumerRegTable");
        Field f = tableClass.getField("consumerInvokers");
        consumerInvokersGetter = LOOKUP.unreflectGetter(f);

        Class<?> wrapperClass =
            Class.forName("org.apache.dubbo.registry.support.ConsumerInvokerWrapper");
        Method m = wrapperClass.getMethod("getRegistryUrl");
        getRegistryUrl = LOOKUP.unreflect(m);
      } catch (Throwable ignored) {
        // ProviderConsumerRegTable not available (Dubbo 2.7.5+ or 3.x)
      }
      CONSUMER_INVOKERS_GETTER = consumerInvokersGetter;
      GET_REGISTRY_URL = getRegistryUrl;
    }

    @Nullable
    @SuppressWarnings("unchecked") // ConcurrentHashMap generic cast from reflective access
    static String extractFromRegTable(URL invokerUrl) {
      if (CONSUMER_INVOKERS_GETTER == null || GET_REGISTRY_URL == null) {
        return null;
      }
      try {
        String serviceKey = invokerUrl.getServiceKey();
        if (serviceKey == null || serviceKey.isEmpty()) {
          return null;
        }
        ConcurrentHashMap<String, Set<?>> table =
            (ConcurrentHashMap<String, Set<?>>) CONSUMER_INVOKERS_GETTER.invoke();
        Set<?> wrappers = table.get(serviceKey);
        if (wrappers == null || wrappers.isEmpty()) {
          return null;
        }
        Object wrapper = wrappers.iterator().next();
        Object urlObj = GET_REGISTRY_URL.invoke(wrapper);
        if (!(urlObj instanceof URL)) {
          return null;
        }
        URL registryUrl = (URL) urlObj;
        return registryUrl.getProtocol() + "://" + registryUrl.getAddress();
      } catch (Throwable t) {
        return null;
      }
    }
  }

  private DubboRegistryUtil() {}
}

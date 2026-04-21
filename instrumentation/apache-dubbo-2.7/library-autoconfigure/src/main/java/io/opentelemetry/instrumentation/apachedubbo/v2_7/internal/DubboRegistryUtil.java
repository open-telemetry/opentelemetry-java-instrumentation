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
   * Used by {@link RegistryCapturingInvoker} while the cluster delegate runs (including into
   * consumer protocol filters). Returns the previous value so callers can restore it.
   */
  @Nullable
  static String pushCapturedRegistryAddress(String address) {
    String previous = CAPTURED_REGISTRY_ADDRESS.get();
    CAPTURED_REGISTRY_ADDRESS.set(address);
    return previous;
  }

  public static void restoreCapturedRegistryAddress(@Nullable String previous) {
    if (previous == null) {
      CAPTURED_REGISTRY_ADDRESS.remove();
    } else {
      CAPTURED_REGISTRY_ADDRESS.set(previous);
    }
  }

  @Nullable
  public static String extractRegistryAddress(RpcInvocation invocation) {
    String captured = CAPTURED_REGISTRY_ADDRESS.get();
    if (captured != null) {
      return captured;
    }

    Invoker<?> invoker = invocation.getInvoker();
    if (invoker == null) {
      return null;
    }

    Directory<?> directory = getDirectory(invoker);
    if (directory != null) {
      String address = tryExtractRegistryAddressFromDirectory(directory);
      if (address != null) {
        return address;
      }
    }

    return null;
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

  /**
   * Resolves {@code protocol://host:port} from a registry-backed directory (for example {@code
   * RegistryDirectory}), using {@code getRegistry()} when present and otherwise the {@code
   * registry} field. Called once per consumer refer when {@link RegistryCapturingClusterWrapper}
   * wraps the cluster invoker.
   */
  @Nullable
  public static String tryExtractRegistryAddressFromDirectory(Directory<?> directory) {
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
      MethodHandle getUrl = findAccessor(registry.getClass(), "getUrl", null, URL_ACCESSOR_CACHE);
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
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      // ignore
    }
    return null;
  }

  @Nullable
  private static MethodHandle resolveField(Class<?> clazz, String name) {
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      try {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return LOOKUP.unreflectGetter(f);
      } catch (NoSuchFieldException | IllegalAccessException ignored) {
        // ignore
      }
    }
    return null;
  }

  private DubboRegistryUtil() {}
}

package com.datadoghq.agent;

import com.google.common.collect.Maps;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.zip.ZipEntry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClassLoaderIntegrationInjector {
  private final Map<ZipEntry, byte[]> entries;
  private final Map<ClassLoader, Method> invocationPoints = Maps.newConcurrentMap();

  public ClassLoaderIntegrationInjector(final Map<ZipEntry, byte[]> entries) {
    this.entries = entries;
  }

  public void inject(final ClassLoader cl) {
    try {
      final Method inovcationPoint = getInovcationPoint(cl);
      final Map<ZipEntry, byte[]> toInject = Maps.newHashMap(entries);
      final Map<ZipEntry, byte[]> injectedEntries = Maps.newHashMap();
      boolean successfulyAdded = true;
      while (!toInject.isEmpty() && successfulyAdded) {
        log.debug("Attempting to inject {} entries into {}", toInject.size(), cl);
        successfulyAdded = false;
        for (final Map.Entry<ZipEntry, byte[]> entry : toInject.entrySet()) {
          final String name = entry.getKey().getName();
          if (!name.endsWith(".class")) {
            continue;
          }
          final byte[] bytes = entry.getValue();
          try {
            inovcationPoint.invoke(cl, bytes, 0, bytes.length);
            injectedEntries.put(entry.getKey(), entry.getValue());
            successfulyAdded = true;
          } catch (final InvocationTargetException e) {
            log.debug(
                "Error calling 'defineClass' method on {} for entry {}: {}",
                cl,
                entry,
                e.getMessage());
            log.debug("Error Details", e);
          }
        }
        toInject.keySet().removeAll(injectedEntries.keySet());
      }

    } catch (final NoSuchMethodException e) {
      log.error("Error getting 'defineClass' method from {}", cl);
    } catch (final IllegalAccessException e) {
      log.error("Error accessing 'defineClass' method on {}", cl);
    }
  }

  private Method getInovcationPoint(final ClassLoader cl) throws NoSuchMethodException {
    if (invocationPoints.containsKey(invocationPoints)) {
      return invocationPoints.get(invocationPoints);
    }
    Class<?> clazz = cl.getClass();
    NoSuchMethodException firstException = null;
    while (clazz != null) {
      try {
        // defineClass is protected so we may need to check up the class hierarchy.
        final Method invocationPoint =
            clazz.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
        invocationPoint.setAccessible(true);
        invocationPoints.put(cl, invocationPoint);
        return invocationPoint;
      } catch (final NoSuchMethodException e) {
        if (firstException == null) {
          firstException = e;
        }
        clazz = clazz.getSuperclass();
      }
    }
    throw firstException;
  }
}

package com.datadoghq.trace.agent;

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

  public ClassLoaderIntegrationInjector(Map<ZipEntry, byte[]> entries) {
    this.entries = entries;
  }

  public void inject(ClassLoader cl) {
    try {
      Method inovcationPoint = getInovcationPoint(cl);
      Map<ZipEntry, byte[]> toInject = Maps.newHashMap(entries);
      Map<ZipEntry, byte[]> injectedEntries = Maps.newHashMap();
      boolean successfulyAdded = true;
      while (!toInject.isEmpty() && successfulyAdded) {
        log.debug("Attempting to inject {} entries into {}", toInject.size(), cl);
        successfulyAdded = false;
        for (Map.Entry<ZipEntry, byte[]> entry : toInject.entrySet()) {
          String name = entry.getKey().getName();
          if (!name.endsWith(".class")) {
            continue;
          }
          byte[] bytes = entry.getValue();
          try {
            inovcationPoint.invoke(cl, bytes, 0, bytes.length);
            injectedEntries.put(entry.getKey(), entry.getValue());
            successfulyAdded = true;
          } catch (InvocationTargetException e) {
            log.debug("Error calling 'defineClass' method on {} for entry {}", cl, entry);
          }
        }
        toInject.keySet().removeAll(injectedEntries.keySet());
      }

    } catch (NoSuchMethodException e) {
      log.error("Error getting 'defineClass' method from {}", cl);
    } catch (IllegalAccessException e) {
      log.error("Error accessing 'defineClass' method on {}", cl);
    }
  }

  private Method getInovcationPoint(ClassLoader cl) throws NoSuchMethodException {
    if (invocationPoints.containsKey(invocationPoints)) {
      return invocationPoints.get(invocationPoints);
    }
    Class<?> clazz = cl.getClass();
    NoSuchMethodException firstException = null;
    while (clazz != null) {
      try {
        // defineClass is protected so we may need to check up the class hierarchy.
        Method invocationPoint =
            clazz.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
        invocationPoint.setAccessible(true);
        invocationPoints.put(cl, invocationPoint);
        return invocationPoint;
      } catch (NoSuchMethodException e) {
        if (firstException == null) {
          firstException = e;
        }
        clazz = clazz.getSuperclass();
      }
    }
    throw firstException;
  }
}

package com.datadoghq.agent;

import com.google.common.collect.Maps;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClassLoaderIntegrationInjector {
  private final Map<ZipEntry, byte[]> entries;
  private final Map<ClassLoader, Method> invocationPoints = Maps.newConcurrentMap();

  public ClassLoaderIntegrationInjector(final Map<ZipEntry, byte[]> entries) {
    this.entries = Maps.newHashMap(entries);
    for (final Iterator<Map.Entry<ZipEntry, byte[]>> it = entries.entrySet().iterator();
        it.hasNext();
        ) {
      final Map.Entry<ZipEntry, byte[]> entry = it.next();
      if (!entry.getKey().getName().endsWith(".class")) {
        // remove all non-class files
        it.remove();
      }
    }
  }

  public void inject(final ClassLoader cl) {
    try {
      final Method inovcationPoint = getInovcationPoint(cl);
      final Map<ZipEntry, byte[]> toInject = Maps.newHashMap(entries);
      final Map<ZipEntry, byte[]> injectedEntries = Maps.newHashMap();
      final List<Throwable> lastErrors = new LinkedList<>();
      boolean successfulyAdded = true;
      while (!toInject.isEmpty() && successfulyAdded) {
        log.debug("Attempting to inject {} entries into {}", toInject.size(), cl);
        successfulyAdded = false;
        lastErrors.clear();
        for (final Map.Entry<ZipEntry, byte[]> entry : toInject.entrySet()) {
          final byte[] bytes = entry.getValue();
          try {
            inovcationPoint.invoke(cl, bytes, 0, bytes.length);
            injectedEntries.put(entry.getKey(), entry.getValue());
            successfulyAdded = true;
          } catch (final InvocationTargetException e) {
            lastErrors.add(e);
          }
        }
        toInject.keySet().removeAll(injectedEntries.keySet());
      }
      log.info("Successfully injected {} classes into {}", injectedEntries.size(), cl);
      log.info("Failed injecting {} classes into {}", toInject.size(), cl);
      log.debug("\nSuccesses: {}", injectedEntries);
      log.debug("\nFailures: {}", toInject);
      for (final Throwable error : lastErrors) {
        log.debug("Injection error", error.getCause());
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

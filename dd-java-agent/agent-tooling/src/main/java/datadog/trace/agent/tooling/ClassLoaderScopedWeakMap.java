package datadog.trace.agent.tooling;

import datadog.trace.bootstrap.WeakMap;
import java.util.HashMap;
import java.util.Map;

public class ClassLoaderScopedWeakMap {

  public static final ClassLoaderScopedWeakMap INSTANCE = new ClassLoaderScopedWeakMap();

  private final WeakMap<ClassLoader, Map<Object, Object>> map = WeakMap.Supplier.DEFAULT.get();

  public synchronized Object getOrCreate(
      ClassLoader classLoader, Object key, Supplier valueSupplier) {
    Map<Object, Object> classLoaderMap = map.get(classLoader);
    if (classLoaderMap == null) {
      classLoaderMap = new HashMap<>();
      map.put(classLoader, classLoaderMap);
    }

    if (classLoaderMap.containsKey(key)) {
      return classLoaderMap.get(key);
    }

    Object value = valueSupplier.get();
    classLoaderMap.put(key, value);
    return value;
  }

  public interface Supplier {
    Object get();
  }
}

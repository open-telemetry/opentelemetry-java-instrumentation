package datadog.trace.agent.tooling;

import datadog.trace.bootstrap.WeakMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A registry which is scoped per-classloader able to hold key-value pairs with weak keys. */
public class ClassLoaderScopedWeakMap {

  public static final ClassLoaderScopedWeakMap INSTANCE = new ClassLoaderScopedWeakMap();

  private final WeakMap<ClassLoader, Map<Object, Object>> map = WeakMap.Supplier.DEFAULT.get();

  /**
   * Gets the element registered at the specified key or register as new one retrieved by the
   * provided supplier.
   */
  public synchronized Object getOrCreate(
      ClassLoader classLoader, Object key, Supplier valueSupplier) {
    Map<Object, Object> classLoaderMap;

    if (!map.containsKey(classLoader)) {
      classLoaderMap = new ConcurrentHashMap<>();
      map.put(classLoader, classLoaderMap);
    } else {
      classLoaderMap = map.get(classLoader);
    }

    if (classLoaderMap.containsKey(key)) {
      return classLoaderMap.get(key);
    }

    final Object value = valueSupplier.get();
    classLoaderMap.put(key, value);
    return value;
  }

  /**
   * Supplies the value to be stored and it is called only when a value does not exists yet in the
   * registry.
   */
  public interface Supplier {
    Object get();
  }
}

package datadog.trace.agent.tooling;

//import datadog.trace.bootstrap.WeakMap;
//import java.util.HashMap;
//import java.util.Map;

/** A registry which is scoped per-classloader able to hold key-value pairs with weak keys. */
public class ClassLoaderScopedWeakMap {

//  public static final ClassLoaderScopedWeakMap INSTANCE = new ClassLoaderScopedWeakMap();

  // private final WeakMap<ClassLoader, Map<Object, Object>> map = WeakMap.Supplier.DEFAULT.get();

  /**
   * Gets the element registered at the specified key or register as new one retrieved by the
   * provided supplier.
   */
//  public Object getOrCreate(
////  public synchronized Object getOrCreate(
//      ClassLoader classLoader, Object key, Supplier valueSupplier) {
////    Map<Object, Object> classLoaderMap = map.get(classLoader);
////    if (classLoaderMap == null) {
////      classLoaderMap = new HashMap<>();
////      map.put(classLoader, classLoaderMap);
////    }
////
////    if (classLoaderMap.containsKey(key)) {
////      return classLoaderMap.get(key);
////    }
//
//    Object value = valueSupplier.get();
////    classLoaderMap.put(key, value);
//    return value;
//  }

  public static Object aaa(Object aaa) {
    System.out.println("[STD LOG] aaa " + ClassLoaderScopedWeakMapSupplier.class.getName());
    return aaa;
  }

  public static Object bbb(ClassLoaderScopedWeakMapSupplier aaa) {
    System.out.println("[STD LOG] bbb" + ClassLoaderScopedWeakMapSupplier.class.getName());
    return aaa.get();
  }

//  /**
//   * Supplies the value to be stored and it is called only when a value does not exists yet in the
//   * registry.
//   */
//  public interface Supplier {
//    Object get();
//  }
}

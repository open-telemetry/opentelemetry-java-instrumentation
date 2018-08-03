package datadog.trace.agent.tooling;

import static net.bytebuddy.agent.builder.AgentBuilder.*;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;

public class DDCachingPoolStrategy implements PoolStrategy {
  private static final Map<ClassLoader, TypePool> typePoolCache =
      Collections.synchronizedMap(new WeakHashMap<ClassLoader, TypePool>());

  @Override
  public TypePool typePool(ClassFileLocator classFileLocator, ClassLoader classLoader) {
    final ClassLoader key = null == classLoader ? Utils.getBootstrapProxy() : classLoader;
    TypePool cachedPool = typePoolCache.get(key);
    if (null == cachedPool) {
      synchronized (key) {
        cachedPool = typePoolCache.get(key);
        if (null == cachedPool) {
          cachedPool = Default.FAST.typePool(classFileLocator, classLoader);
          typePoolCache.put(key, cachedPool);
        }
      }
    }
    return cachedPool;
  }
}

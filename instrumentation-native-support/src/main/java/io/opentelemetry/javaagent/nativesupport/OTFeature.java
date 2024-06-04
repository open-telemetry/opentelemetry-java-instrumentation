package io.opentelemetry.javaagent.nativesupport;

import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNamesDelegate;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;

/**
 * Register reflections used in OT code like:
 * <pre>
 *      VirtualField&lt;Callable&lt;?&gt;, PropagatedContext&gt; virtualField = VirtualField.find(Callable.class, PropagatedContext.class);
 * </pre>
 * The {@code find} method uses reflection. But it can be replaced to reflection result by OT Gradle build plugin at jar build time.
 */
@SuppressWarnings({"NotJavadoc", "UnescapedEntity", "rawtypes", "unchecked"})
public class OTFeature implements Feature {

  private static final Pair<Class<?>, Class<?>>[] virtualFieldClassPairs = new Pair[] {
      /** from {@link ExecutorsAspect} */
      Pair.create(Runnable.class, PropagatedContext.class),
      Pair.create(ForkJoinTask.class, PropagatedContext.class),
      Pair.create(Future.class, PropagatedContext.class),
      Pair.create(Callable.class, PropagatedContext.class)
  };

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess a) {
    for (Pair<Class<?>, Class<?>> virtualFieldClassPair : virtualFieldClassPairs) {
      String virtualFieldClassName = GeneratedVirtualFieldNamesDelegate
          .getVirtualFieldImplementationClassName(virtualFieldClassPair.getLeft().getTypeName(),
              virtualFieldClassPair.getRight().getTypeName());
      try {
        Class<?> c = a.findClassByName(virtualFieldClassName);
        RuntimeReflection.register(c);
        Method m = c.getDeclaredMethod("getVirtualField", Class.class, Class.class);
        RuntimeReflection.register(m);
      } catch (ReflectiveOperationException e) {
        System.out.println("Warning: Can't find " + virtualFieldClassName
            + "#getVirtualField(Class,Class) method");
      }
    }
  }
}

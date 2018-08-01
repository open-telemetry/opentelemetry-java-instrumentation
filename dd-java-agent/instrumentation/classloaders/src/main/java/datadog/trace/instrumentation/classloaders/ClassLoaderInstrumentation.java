package datadog.trace.instrumentation.classloaders;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;

import com.google.auto.service.AutoService;
import datadog.opentracing.DDTracer;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class ClassLoaderInstrumentation extends Instrumenter.Default {

  public ClassLoaderInstrumentation() {
    super("classloader");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher typeMatcher() {
    return safeHasSuperType(named("java.lang.ClassLoader"));
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(isConstructor(), ClassloaderAdvice.class.getName());
    return transformers;
  }

  public static class ClassloaderAdvice {

    @Advice.OnMethodEnter
    public static int constructorEnter() {
      // We use this to make sure we only apply the exit instrumentation
      // after the constructors are done calling their super constructors.
      return CallDepthThreadLocalMap.incrementCallDepth(ClassLoader.class);
    }

    // Not sure why, but adding suppress causes a verify error.
    @Advice.OnMethodExit // (suppress = Throwable.class)
    public static void constructorExit(
        @Advice.This final ClassLoader cl, @Advice.Enter final int depth) {
      if (depth == 0) {
        CallDepthThreadLocalMap.reset(ClassLoader.class);

        try {
          final Field field = GlobalTracer.class.getDeclaredField("tracer");
          field.setAccessible(true);

          final Object o = field.get(null);
          // FIXME: This instrumentation will never work. Referencing class DDTracer will throw an
          // exception.
          if (o instanceof DDTracer) {
            final DDTracer tracer = (DDTracer) o;
            tracer.registerClassLoader(cl);
          }
        } catch (final Throwable e) {
        }
      }
    }
  }
}

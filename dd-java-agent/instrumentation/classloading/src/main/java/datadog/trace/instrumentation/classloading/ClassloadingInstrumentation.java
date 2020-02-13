package datadog.trace.instrumentation.classloading;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Constants;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/*
 * Some class loaders do not delegate to their parent, so classes in those class loaders
 * will not be able to see classes in the bootstrap class loader.
 *
 * In particular, instrumentation on classes in those class loaders will not be able to see
 * the shaded OpenTelemetry API classes in the bootstrap class loader.
 *
 * This instrumentation forces all class loaders to delegate to the bootstrap class loader
 * for the classes that we have put in the bootstrap class loader.
 */
@AutoService(Instrumenter.class)
public final class ClassloadingInstrumentation extends Instrumenter.Default {
  public ClassloadingInstrumentation() {
    super("classloading");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // just an optimization to exclude common class loaders that are known to delegate to the
    // bootstrap loader (or happen to _be_ the bootstrap loader)
    return not(named("java.lang.ClassLoader"))
        .and(not(named("com.ibm.oti.vm.BootstrapClassLoader")))
        .and(not(named("datadog.trace.bootstrap.AgentClassLoader")))
        .and(safeHasSuperType(named("java.lang.ClassLoader")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {Constants.class.getName()};
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("loadClass"))
            .and(
                takesArguments(1)
                    .and(takesArgument(0, named("java.lang.String")))
                    .or(
                        takesArguments(2)
                            .and(takesArgument(0, named("java.lang.String")))
                            .and(takesArgument(1, named("boolean")))))
            .and(isPublic().or(isProtected()))
            .and(not(isStatic())),
        ClassloadingInstrumentation.class.getName() + "$LoadClassAdvice");
  }

  public static class LoadClassAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Class<?> onEnter(
        @Advice.Argument(0) final String name, @Advice.Local("_callDepth") int callDepth) {
      callDepth = CallDepthThreadLocalMap.incrementCallDepth(ClassLoader.class);
      if (callDepth > 0) {
        return null;
      }
      for (final String prefix : Constants.BOOTSTRAP_PACKAGE_PREFIXES) {
        if (name.startsWith(prefix)) {
          try {
            return Class.forName(name, false, null);
          } catch (final ClassNotFoundException e) {
          }
        }
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Local("_callDepth") final int callDepth,
        @Advice.Return(readOnly = false) Class<?> result,
        @Advice.Enter final Class<?> resultFromBootstrapLoader) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(ClassLoader.class);
      if (resultFromBootstrapLoader != null) {
        result = resultFromBootstrapLoader;
      }
    }
  }
}

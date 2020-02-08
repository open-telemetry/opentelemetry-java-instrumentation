package io.opentelemetry.auto.instrumentation.classloading;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
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
import io.opentelemetry.auto.tooling.Constants;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class ClassloadingInstrumentation extends Instrumenter.Default {
  public ClassloadingInstrumentation() {
    super("classloading");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("java.lang.ClassLoader"));
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
    public static Class<?> onEnter(@Advice.Argument(0) final String name) {
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

    @Advice.OnMethodExit
    public static void onExit(
        @Advice.Return(readOnly = false) Class<?> result, @Advice.Enter final Class<?> clazz) {
      if (clazz != null) {
        result = clazz;
      }
    }
  }
}

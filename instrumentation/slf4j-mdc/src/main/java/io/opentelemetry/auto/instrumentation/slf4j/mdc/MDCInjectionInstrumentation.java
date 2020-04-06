package io.opentelemetry.auto.instrumentation.slf4j.mdc;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.api.Config;
import io.opentelemetry.auto.api.GlobalTracer;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.auto.tooling.log.LogContextScopeListener;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

@AutoService(Instrumenter.class)
public class MDCInjectionInstrumentation extends Instrumenter.Default {

  // Intentionally doing the string replace to bypass gradle shadow rename
  // mdcClassName = org.slf4j.MDC
  private static final String mdcClassName = "org.TMP.MDC".replaceFirst("TMP", "slf4j");

  public MDCInjectionInstrumentation() {
    super("slf4j-mdc");
  }

  @Override
  protected boolean defaultEnabled() {
    return Config.get().isLogInjectionEnabled();
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named(mdcClassName);
  }

  @Override
  public void postMatch(
      final TypeDescription typeDescription,
      final ClassLoader classLoader,
      final JavaModule module,
      final Class<?> classBeingRedefined,
      final ProtectionDomain protectionDomain) {
    if (classBeingRedefined != null) {
      MDCAdvice.mdcClassInitialized(classBeingRedefined);
    }
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isTypeInitializer(), MDCInjectionInstrumentation.class.getName() + "$MDCAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {LogContextScopeListener.class.getName()};
  }

  public static class MDCAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void mdcClassInitialized(@Advice.Origin final Class mdcClass) {
      try {
        final Method putMethod = mdcClass.getMethod("put", String.class, String.class);
        final Method removeMethod = mdcClass.getMethod("remove", String.class);
        GlobalTracer.get().addScopeListener(new LogContextScopeListener(putMethod, removeMethod));
      } catch (final NoSuchMethodException e) {
        org.slf4j.LoggerFactory.getLogger(mdcClass).debug("Failed to add MDC span listener", e);
      }
    }
  }
}

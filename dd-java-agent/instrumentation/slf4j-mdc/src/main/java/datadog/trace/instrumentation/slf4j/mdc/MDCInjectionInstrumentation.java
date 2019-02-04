package datadog.trace.instrumentation.slf4j.mdc;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import datadog.trace.api.CorrelationIdentifier;
import datadog.trace.api.GlobalTracer;
import datadog.trace.context.ScopeListener;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

@AutoService(Instrumenter.class)
public class MDCInjectionInstrumentation extends Instrumenter.Default {
  public static final String MDC_INSTRUMENTATION_NAME = "mdc";

  // Intentionally doing the string replace to bypass gradle shadow rename
  // mdcClassName = org.slf4j.MDC
  private static final String mdcClassName = "org.TMP.MDC".replaceFirst("TMP", "slf4j");

  public MDCInjectionInstrumentation() {
    super(MDC_INSTRUMENTATION_NAME);
  }

  @Override
  protected boolean defaultEnabled() {
    return Config.getBooleanSettingFromEnvironment(
        Config.LOGS_INJECTION_ENABLED, Config.DEFAULT_LOGS_INJECTION_ENABLED);
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
    return singletonMap(isTypeInitializer(), MDCAdvice.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {MDCAdvice.class.getName() + "$MDCScopeListener"};
  }

  public static class MDCAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void mdcClassInitialized(@Advice.Origin final Class mdcClass) {
      try {
        final Method putMethod = mdcClass.getMethod("put", String.class, String.class);
        final Method removeMethod = mdcClass.getMethod("remove", String.class);
        GlobalTracer.get().addScopeListener(new MDCScopeListener(putMethod, removeMethod));
      } catch (final NoSuchMethodException e) {
        org.slf4j.LoggerFactory.getLogger(mdcClass).debug("Failed to add MDC span listener", e);
      }
    }

    @Slf4j
    public static class MDCScopeListener implements ScopeListener {
      private final Method putMethod;
      private final Method removeMethod;

      public MDCScopeListener(final Method putMethod, final Method removeMethod) {
        this.putMethod = putMethod;
        this.removeMethod = removeMethod;
      }

      @Override
      public void afterScopeActivated() {
        try {
          putMethod.invoke(
              null, CorrelationIdentifier.getTraceIdKey(), CorrelationIdentifier.getTraceId());
          putMethod.invoke(
              null, CorrelationIdentifier.getSpanIdKey(), CorrelationIdentifier.getSpanId());
        } catch (final Exception e) {
          log.debug("Exception setting mdc context", e);
        }
      }

      @Override
      public void afterScopeClosed() {
        try {
          removeMethod.invoke(null, CorrelationIdentifier.getTraceIdKey());
          removeMethod.invoke(null, CorrelationIdentifier.getSpanIdKey());
        } catch (final Exception e) {
          log.debug("Exception removing mdc context", e);
        }
      }
    }
  }
}

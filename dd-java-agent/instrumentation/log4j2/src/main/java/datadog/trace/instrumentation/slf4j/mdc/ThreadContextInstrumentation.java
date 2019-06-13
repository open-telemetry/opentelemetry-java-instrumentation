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
public class ThreadContextInstrumentation extends Instrumenter.Default {
  public static final String MDC_INSTRUMENTATION_NAME = "log4j-thread-context";

  private static final String mdcClassName = "org.apache.logging.log4j.ThreadContext";

  public ThreadContextInstrumentation() {
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
      ThreadContextAdvice.mdcClassInitialized(classBeingRedefined);
    }
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(isTypeInitializer(), ThreadContextAdvice.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {ThreadContextAdvice.class.getName() + "$ThreadContextScopeListener"};
  }

  public static class ThreadContextAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void mdcClassInitialized(@Advice.Origin final Class threadClass) {
      try {
        final Method putMethod = threadClass.getMethod("put", String.class, String.class);
        final Method removeMethod = threadClass.getMethod("remove", String.class);
        GlobalTracer.get().addScopeListener(new ThreadContextScopeListener(putMethod, removeMethod));
      } catch (final NoSuchMethodException e) {
        org.slf4j.LoggerFactory.getLogger(threadClass).debug("Failed to add log4j ThreadContext span listener", e);
      }
    }

    @Slf4j
    public static class ThreadContextScopeListener implements ScopeListener {
      private final Method putMethod;
      private final Method removeMethod;

      public ThreadContextScopeListener(final Method putMethod, final Method removeMethod) {
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
          log.debug("Exception setting thread context context", e);
        }
      }

      @Override
      public void afterScopeClosed() {
        try {
          removeMethod.invoke(null, CorrelationIdentifier.getTraceIdKey());
          removeMethod.invoke(null, CorrelationIdentifier.getSpanIdKey());
        } catch (final Exception e) {
          log.debug("Exception removing thread context context", e);
        }
      }
    }
  }
}

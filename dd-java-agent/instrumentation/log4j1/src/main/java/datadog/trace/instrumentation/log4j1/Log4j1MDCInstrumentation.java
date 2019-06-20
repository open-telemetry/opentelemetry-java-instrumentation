package datadog.trace.instrumentation.log4j1;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import datadog.trace.api.CorrelationIdentifier;
import datadog.trace.api.GlobalTracer;
import datadog.trace.context.ScopeListener;
import java.lang.reflect.Method;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class Log4j1MDCInstrumentation extends Instrumenter.Default {
  public static final String MDC_INSTRUMENTATION_NAME = "log4j-mdc";

  private static final String mdcClassName = "org.apache.log4j.MDC";

  public Log4j1MDCInstrumentation() {
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
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(isConstructor(), MDCContextAdvice.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {MDCContextAdvice.class.getName() + "$MDCScopeListener"};
  }

  public static class MDCContextAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void mdcClassInitialized(@Advice.This Object instance) {
      try {
        if (instance == null) {
          return;
        }

        Class<?> mdcClass = instance.getClass();
        final Method putMethod = mdcClass.getMethod("put", String.class, Object.class);
        final Method removeMethod = mdcClass.getMethod("remove", String.class);
        GlobalTracer.get().addScopeListener(new MDCScopeListener(putMethod, removeMethod));
      } catch (final NoSuchMethodException e) {
      }
    }

    @Slf4j
    public static class MDCScopeListener implements ScopeListener {
      private final Method putMethod;
      private final Method removeMethod;

      public MDCScopeListener(final Method putMethod, final Method removeMethod) {
        System.out.println("Initializing scope listener");
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

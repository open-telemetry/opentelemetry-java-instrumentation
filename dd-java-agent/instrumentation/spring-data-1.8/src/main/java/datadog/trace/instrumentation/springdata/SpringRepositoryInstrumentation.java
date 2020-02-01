// This file includes software developed at SignalFx

package datadog.trace.instrumentation.springdata;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.springdata.SpringDataDecorator.DECORATOR;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;

@AutoService(Instrumenter.class)
public final class SpringRepositoryInstrumentation extends Instrumenter.Default {

  public SpringRepositoryInstrumentation() {
    super("spring-data");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(named("org.springframework.data.repository.core.support.RepositoryFactorySupport"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      packageName + ".SpringDataDecorator",
      getClass().getName() + "$RepositoryInterceptor",
      getClass().getName() + "$InterceptingRepositoryProxyPostProcessor",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isConstructor(),
        SpringRepositoryInstrumentation.class.getName() + "$RepositoryFactorySupportAdvice");
  }

  public static class RepositoryFactorySupportAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onConstruction(
        @Advice.This final RepositoryFactorySupport repositoryFactorySupport) {
      repositoryFactorySupport.addRepositoryProxyPostProcessor(
          InterceptingRepositoryProxyPostProcessor.INSTANCE);
    }

    // Muzzle doesn't detect the "Override" implementation dependency, so we have to help it.
    private void muzzleCheck(final RepositoryProxyPostProcessor processor) {
      processor.postProcess(null, null);
      // (see usage in InterceptingRepositoryProxyPostProcessor below)
    }
  }

  public static final class InterceptingRepositoryProxyPostProcessor
      implements RepositoryProxyPostProcessor {
    public static final RepositoryProxyPostProcessor INSTANCE =
        new InterceptingRepositoryProxyPostProcessor();

    // DQH - TODO: Support older versions?
    // The signature of postProcess changed to add RepositoryInformation in
    // spring-data-commons 1.9.0
    // public void postProcess(final ProxyFactory factory) {
    //   factory.addAdvice(0, RepositoryInterceptor.INSTANCE);
    // }

    @Override
    public void postProcess(
        final ProxyFactory factory, final RepositoryInformation repositoryInformation) {
      factory.addAdvice(0, RepositoryInterceptor.INSTANCE);
    }
  }

  static final class RepositoryInterceptor implements MethodInterceptor {
    public static final MethodInterceptor INSTANCE = new RepositoryInterceptor();

    private RepositoryInterceptor() {}

    @Override
    public Object invoke(final MethodInvocation methodInvocation) throws Throwable {
      final Method invokedMethod = methodInvocation.getMethod();
      final Class<?> clazz = invokedMethod.getDeclaringClass();

      final boolean isRepositoryOp = Repository.class.isAssignableFrom(clazz);
      // Since this interceptor is the outer most interceptor, non-Repository methods
      // including Object methods will also flow through here.  Don't create spans for those.
      if (!isRepositoryOp) {
        return methodInvocation.proceed();
      }

      final AgentSpan span = startSpan("repository.operation");
      DECORATOR.afterStart(span);
      DECORATOR.onOperation(span, invokedMethod);

      final AgentScope scope = activateSpan(span, true);
      scope.setAsyncPropagation(true);

      Object result = null;
      try {
        result = methodInvocation.proceed();
      } catch (final Throwable t) {
        DECORATOR.onError(scope, t);
        throw t;
      } finally {
        DECORATOR.beforeFinish(scope);
        scope.close();
      }
      return result;
    }
  }
}

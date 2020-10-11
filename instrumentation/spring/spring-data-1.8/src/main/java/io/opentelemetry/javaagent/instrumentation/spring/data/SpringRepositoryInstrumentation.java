/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data;

import static io.opentelemetry.javaagent.instrumentation.spring.data.SpringDataTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
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
    return named("org.springframework.data.repository.core.support.RepositoryFactorySupport");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".SpringDataTracer",
      getClass().getName() + "$RepositoryInterceptor",
      getClass().getName() + "$InterceptingRepositoryProxyPostProcessor",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor(),
        SpringRepositoryInstrumentation.class.getName() + "$RepositoryFactorySupportAdvice");
  }

  public static class RepositoryFactorySupportAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onConstruction(
        @Advice.This RepositoryFactorySupport repositoryFactorySupport) {
      repositoryFactorySupport.addRepositoryProxyPostProcessor(
          InterceptingRepositoryProxyPostProcessor.INSTANCE);
    }

    // Muzzle doesn't detect the "Override" implementation dependency, so we have to help it.
    private void muzzleCheck(RepositoryProxyPostProcessor processor) {
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
    public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
      factory.addAdvice(0, RepositoryInterceptor.INSTANCE);
    }
  }

  static final class RepositoryInterceptor implements MethodInterceptor {
    public static final MethodInterceptor INSTANCE = new RepositoryInterceptor();

    private RepositoryInterceptor() {}

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      Method invokedMethod = methodInvocation.getMethod();
      Class<?> clazz = invokedMethod.getDeclaringClass();

      boolean isRepositoryOp = Repository.class.isAssignableFrom(clazz);
      // Since this interceptor is the outer most interceptor, non-Repository methods
      // including Object methods will also flow through here.  Don't create spans for those.
      if (!isRepositoryOp) {
        return methodInvocation.proceed();
      }

      Span span = TRACER.startSpan(invokedMethod);

      Object result;
      try (Scope ignored = currentContextWith(span)) {
        result = methodInvocation.proceed();
        TRACER.end(span);
      } catch (Throwable t) {
        TRACER.endExceptionally(span, t);
        throw t;
      }
      return result;
    }
  }
}

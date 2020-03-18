/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.springdata;

import static io.opentelemetry.auto.instrumentation.springdata.SpringDataDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.springdata.SpringDataDecorator.TRACER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
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
      packageName + ".SpringDataDecorator",
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

      final Span span = TRACER.spanBuilder(DECORATE.spanNameForMethod(invokedMethod)).startSpan();
      DECORATE.afterStart(span);

      final Scope scope = TRACER.withSpan(span);

      Object result = null;
      try {
        result = methodInvocation.proceed();
      } catch (final Throwable t) {
        DECORATE.onError(span, t);
        throw t;
      } finally {
        DECORATE.beforeFinish(span);
        span.end();
        scope.close();
      }
      return result;
    }
  }
}

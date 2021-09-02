/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.spring.data.SpringDataSingletons.instrumenter;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;

@AutoService(InstrumentationModule.class)
public class SpringDataInstrumentationModule extends InstrumentationModule {

  public SpringDataInstrumentationModule() {
    super("spring-data", "spring-data-1.8");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RepositoryFactorySupportInstrumentation());
  }

  private static final class RepositoryFactorySupportInstrumentation
      implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.springframework.data.repository.core.support.RepositoryFactorySupport");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          isConstructor(),
          SpringDataInstrumentationModule.class.getName() + "$RepositoryFactorySupportAdvice");
    }
  }

  @SuppressWarnings("unused")
  public static class RepositoryFactorySupportAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onConstruction(
        @Advice.This RepositoryFactorySupport repositoryFactorySupport) {
      repositoryFactorySupport.addRepositoryProxyPostProcessor(
          InterceptingRepositoryProxyPostProcessor.INSTANCE);
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
      Context parentContext = currentContext();
      Method method = methodInvocation.getMethod();
      // Since this interceptor is the outer most interceptor, non-Repository methods
      // including Object methods will also flow through here.  Don't create spans for those.
      boolean isRepositoryOp = Repository.class.isAssignableFrom(method.getDeclaringClass());
      if (!isRepositoryOp || !instrumenter().shouldStart(parentContext, method)) {
        return methodInvocation.proceed();
      }

      Context context = instrumenter().start(parentContext, method);
      try (Scope ignored = context.makeCurrent()) {
        Object result = methodInvocation.proceed();
        instrumenter().end(context, method, null, null);
        return result;
      } catch (Throwable t) {
        instrumenter().end(context, method, null, t);
        throw t;
      }
    }
  }
}

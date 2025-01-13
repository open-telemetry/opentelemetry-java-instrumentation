/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data.v1_8;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.spring.data.v1_8.SpringDataSingletons.instrumenter;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
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
      factory.addAdvice(
          0, new RepositoryInterceptor(repositoryInformation.getRepositoryInterface()));
    }
  }

  static final class RepositoryInterceptor implements MethodInterceptor {
    private static final Class<?> MONO_CLASS = loadClass("reactor.core.publisher.Mono");
    private final Class<?> repositoryInterface;

    RepositoryInterceptor(Class<?> repositoryInterface) {
      this.repositoryInterface = repositoryInterface;
    }

    private static Class<?> loadClass(String name) {
      try {
        return Class.forName(name);
      } catch (ClassNotFoundException exception) {
        return null;
      }
    }

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      Context parentContext = currentContext();
      Method method = methodInvocation.getMethod();
      // Since this interceptor is the outermost interceptor, non-Repository methods
      // including Object methods will also flow through here. Don't create spans for those.
      boolean isRepositoryOp = !Object.class.equals(method.getDeclaringClass());
      ClassAndMethod classAndMethod = ClassAndMethod.create(repositoryInterface, method.getName());
      if (!isRepositoryOp || !instrumenter().shouldStart(parentContext, classAndMethod)) {
        return methodInvocation.proceed();
      }

      Context context = instrumenter().start(parentContext, classAndMethod);

      Object result;
      try (Scope ignored = context.makeCurrent()) {
        result = methodInvocation.proceed();
      } catch (Throwable t) {
        instrumenter().end(context, classAndMethod, null, t);
        throw t;
      }
      Class<?> type = method.getReturnType();
      // the return type for
      // org.springframework.data.repository.kotlin.CoroutineCrudRepository#findById
      // is Object but the method may actually return a Mono
      if (Object.class == type && MONO_CLASS != null && MONO_CLASS.isInstance(result)) {
        type = MONO_CLASS;
      }
      return AsyncOperationEndSupport.create(instrumenter(), Void.class, type)
          .asyncEnd(context, classAndMethod, result, null);
    }
  }
}

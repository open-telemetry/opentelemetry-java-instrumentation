/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.server;

import static io.opentelemetry.javaagent.bootstrap.rmi.ThreadLocalContext.THREAD_LOCAL_CONTEXT;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.rmi.server.RmiServerSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.rmi.Remote;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RemoteServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.rmi.Remote"))
        .and(not(nameStartsWith("org.springframework.remoting")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(not(isStatic())),
        this.getClass().getName() + "$PublicMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class PublicMethodAdvice {

    public static class AdviceScope {
      private final CallDepth callDepth;
      private final ClassAndMethod classAndMethod;
      private final Context context;
      private final Scope scope;

      private AdviceScope(
          CallDepth callDepth,
          @Nullable ClassAndMethod classAndMethod,
          @Nullable Context context,
          @Nullable Scope scope) {
        this.callDepth = callDepth;
        this.classAndMethod = classAndMethod;
        this.context = context;
        this.scope = scope;
      }

      public static AdviceScope start(
          CallDepth callDepth, Class<?> declaringClass, String methodName) {
        if (callDepth.getAndIncrement() > 0) {
          return new AdviceScope(callDepth, null, null, null);
        }

        // TODO review and unify with all other SERVER instrumentation
        Context parentContext = THREAD_LOCAL_CONTEXT.getAndResetContext();
        if (parentContext == null) {
          return new AdviceScope(callDepth, null, null, null);
        }
        ClassAndMethod classAndMethod = ClassAndMethod.create(declaringClass, methodName);
        if (!instrumenter().shouldStart(parentContext, classAndMethod)) {
          return new AdviceScope(callDepth, null, null, null);
        }
        Context context = instrumenter().start(parentContext, classAndMethod);
        return new AdviceScope(callDepth, classAndMethod, context, context.makeCurrent());
      }

      public void end(Throwable throwable) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }
        if (scope == null || context == null || classAndMethod == null) {
          return;
        }
        scope.close();
        instrumenter().end(context, classAndMethod, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.Origin("#t") Class<?> declaringClass, @Advice.Origin("#m") String methodName) {
      return AdviceScope.start(CallDepth.forClass(Remote.class), declaringClass, methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }
}

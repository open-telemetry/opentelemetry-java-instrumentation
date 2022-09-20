/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.graphql.mapping;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.spring.graphql.mapping.SchemaMappingSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SchemaMappingInstrumentation implements TypeInstrumentation {

  private static final String[] ANNOTATION_CLASSES =
      new String[] {
          "org.springframework.graphql.data.method.annotation.SchemaMapping",
          "org.springframework.graphql.data.method.annotation.QueryMapping",
          "org.springframework.graphql.data.method.annotation.MutationMapping",
          "org.springframework.graphql.data.method.annotation.SubscriptionMapping"
      };

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.graphql.server.WebGraphQlHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return declaresMethod(isAnnotatedWith(namedOneOf(ANNOTATION_CLASSES)));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isAnnotatedWith(namedOneOf(ANNOTATION_CLASSES))),
        this.getClass().getName() + "$SchemaMappingAdvice"
    );
  }

  @SuppressWarnings("unused")
  public static class SchemaMappingAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startSpan(
        @Advice.Origin("#t") Class<?> codeClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelRequest") SchemaMappingRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = currentContext();
      request = SchemaMappingRequest.create(codeClass, methodName);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") SchemaMappingRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }

      scope.close();
      instrumenter().end(context, request, null, throwable);
    }

  }

}

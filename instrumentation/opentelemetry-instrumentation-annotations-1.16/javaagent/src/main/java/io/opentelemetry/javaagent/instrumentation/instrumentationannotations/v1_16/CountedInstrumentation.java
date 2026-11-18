/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v1_16;

import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v1_16.KotlinCoroutineUtil.isKotlinSuspendMethod;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.constant.NullConstant;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.matcher.ElementMatcher;
import org.objectweb.asm.ClassWriter;

class CountedInstrumentation implements TypeInstrumentation {

  private final ElementMatcher.Junction<MethodDescription> annotatedMethodMatcher;
  private final ElementMatcher.Junction<MethodDescription> excludedMethodsMatcher;

  CountedInstrumentation() {
    annotatedMethodMatcher =
        isMethod()
            .and(
                isAnnotatedWith(
                    named("application.io.opentelemetry.instrumentation.annotations.Counted")));
    excludedMethodsMatcher = AnnotationExcludedMethods.configureExcludedMethods();
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return declaresMethod(annotatedMethodMatcher);
  }

  @Override
  public void transform(TypeTransformer transformer) {
    ElementMatcher.Junction<MethodDescription> countedMethods =
        annotatedMethodMatcher.and(not(excludedMethodsMatcher));
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
          if (declaresMethod(countedMethods.and(isKotlinSuspendMethod()))
              .matches(typeDescription)) {
            // Counted advice can be combined with coroutine bytecode rewriting on this class.
            return builder.visit(
                new AsmVisitorWrapper.ForDeclaredMethods().writerFlags(ClassWriter.COMPUTE_FRAMES));
          }
          return builder;
        });
    transformer.applyAdviceToMethod(
        countedMethods,
        mapping ->
            mapping.bind(
                KotlinContinuation.class,
                (instrumentedType, instrumentedMethod, assigner, argumentHandler, sort) -> {
                  StackManipulation continuation = NullConstant.INSTANCE;
                  if (isKotlinSuspendMethod().matches(instrumentedMethod)) {
                    int lastParameter = instrumentedMethod.getParameters().size() - 1;
                    int offset = instrumentedMethod.getParameters().get(lastParameter).getOffset();
                    continuation =
                        MethodVariableAccess.REFERENCE.loadFrom(argumentHandler.argument(offset));
                  }
                  return new Advice.OffsetMapping.Target.ForStackManipulation(continuation);
                }),
        getClass().getName() + "$CountedAdvice");
  }

  @interface KotlinContinuation {}

  @SuppressWarnings("unused")
  public static class CountedAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.Origin Method method, @KotlinContinuation @Nullable Object continuation) {
      CountedSingletons.increment(method, continuation);
    }
  }
}

package io.opentelemetry.javaagent.instrumentation.classhook;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class ClassHookInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.azure.spring.cloud.test.config.client");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic(),
        this.getClass().getName() + "$ClassHookMethodAdvice");
  }

  @SuppressWarnings("unused")
  static class ClassHookMethodAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Local("otelScope") Scope scope) {
      System.out.println("Entering public function " + scope.getClass().getName());
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Local("otelScope") Scope scope) {
      System.out.println("Leaving public function " + scope.getClass().getName());
    }
  }
}

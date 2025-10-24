/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis.v3_2;

import static io.opentelemetry.javaagent.instrumentation.mybatis.v3_2.MyBatisSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.ibatis.binding.MapperMethod.SqlCommand;

public class MapperMethodInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.ibatis.binding.MapperMethod");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute"), MapperMethodInstrumentation.class.getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    public static class AdviceScope {
      private final ClassAndMethod classAndMethod;
      private final Context context;
      private final Scope scope;

      private AdviceScope(ClassAndMethod classAndMethod, Context context, Scope scope) {
        this.classAndMethod = classAndMethod;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(@Nullable SqlCommand command) {
        if (command == null) {
          return null;
        }
        ClassAndMethod classAndMethod = SqlCommandUtil.getClassAndMethod(command);
        if (classAndMethod == null) {
          return null;
        }
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, classAndMethod)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, classAndMethod);
        return new AdviceScope(classAndMethod, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        instrumenter().end(context, classAndMethod, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope getMapperInfo(@Advice.FieldValue("command") SqlCommand command) {
      return AdviceScope.start(command);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}

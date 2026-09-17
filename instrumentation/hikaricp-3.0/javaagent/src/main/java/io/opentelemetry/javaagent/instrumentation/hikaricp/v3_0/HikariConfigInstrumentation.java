/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hikaricp.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.zaxxer.hikari.HikariConfig;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class HikariConfigInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.zaxxer.hikari.HikariConfig");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("validate").and(takesArguments(0)), getClass().getName() + "$ValidateAdvice");
    transformer.applyAdviceToMethod(
        named("setPoolName").and(takesArguments(1)).and(takesArgument(0, String.class)),
        getClass().getName() + "$SetPoolNameAdvice");
    transformer.applyAdviceToMethod(
        named("copyStateTo")
            .and(takesArguments(1))
            .and(takesArgument(0, named("com.zaxxer.hikari.HikariConfig"))),
        getClass().getName() + "$CopyStateToAdvice");
  }

  @SuppressWarnings("unused")
  public static class ValidateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.This HikariConfig config) {
      if (config.getPoolName() == null) {
        HikariSingletons.setGeneratedPoolName(config, true);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SetPoolNameAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This HikariConfig config) {
      HikariSingletons.setGeneratedPoolName(config, false);
    }
  }

  @SuppressWarnings("unused")
  public static class CopyStateToAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This HikariConfig source, @Advice.Argument(0) HikariConfig target) {
      HikariSingletons.copyGeneratedPoolName(source, target);
    }
  }
}

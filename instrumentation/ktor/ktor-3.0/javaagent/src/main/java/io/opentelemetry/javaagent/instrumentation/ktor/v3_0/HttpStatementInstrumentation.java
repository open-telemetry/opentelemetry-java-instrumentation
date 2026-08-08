/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ktor.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.ktor.common.v2_0.internal.KtorClientTelemetryUtil;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class HttpStatementInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.ktor.client.statement.HttpStatement");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(takesArgument(0, named("kotlin.jvm.functions.Function2"))),
        getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(@Advice.Argument(0) Object block) {
      return KtorClientTelemetryUtil.wrapWithClientSpanContext(block);
    }
  }
}

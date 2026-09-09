/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Mono;

class McpClientSessionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.modelcontextprotocol.spec.McpClientSession");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("sendRequest")
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, Object.class))
            .and(takesArgument(2, named("io.modelcontextprotocol.json.TypeRef")))
            .and(returns(named("reactor.core.publisher.Mono"))),
        getClass().getName() + "$SendRequestAdvice");
    transformer.applyAdviceToMethod(
        named("generateRequestId").and(takesNoArguments()).and(returns(String.class)),
        getClass().getName() + "$GenerateRequestIdAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendRequestAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.Argument(0) String method, @Advice.Argument(1) @Nullable Object requestParameters) {
      McpToolCallState state = McpToolCallState.prepare(method, requestParameters);
      Object parameters = state == null ? requestParameters : state.getRequestParameters();
      return new Object[] {parameters, state};
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static Mono<?> onExit(
        @Advice.Return @Nullable Mono<?> publisher, @Advice.Enter @Nullable Object[] enterResult) {
      if (enterResult == null) {
        return publisher;
      }
      McpToolCallState state = (McpToolCallState) enterResult[1];
      return state == null ? publisher : state.finish(publisher);
    }
  }

  @SuppressWarnings("unused")
  public static class GenerateRequestIdAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Return String requestId) {
      McpToolCallState.captureRequestId(requestId);
    }
  }
}

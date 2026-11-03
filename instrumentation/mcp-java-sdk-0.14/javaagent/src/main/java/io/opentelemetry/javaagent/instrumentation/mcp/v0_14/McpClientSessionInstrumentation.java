/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.javaagent.instrumentation.mcp.v0_14.McpSingletons.currentState;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientSession;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
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

    @Advice.OnMethodEnter(
        skipOn = Advice.OnNonDefaultValue.class,
        suppress = Throwable.class,
        inline = false)
    @Nullable
    public static Mono<?> onEnter(
        @Advice.This McpClientSession session,
        @Advice.Argument(0) String method,
        @Advice.Argument(1) @Nullable Object requestParameters,
        @Advice.Argument(2) TypeRef<?> responseType) {
      McpToolCallState state = McpToolCallState.prepare(session, method, requestParameters);
      return state == null ? null : state.wrap(responseType);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Nullable
    public static Mono<?> onExit(
        @Advice.This McpClientSession session,
        @Advice.Argument(0) String method,
        @Advice.Return @Nullable Mono<?> publisher,
        @Advice.Enter @Nullable Mono<?> deferredPublisher) {
      return McpProtocolVersionState.capture(
          session, method, deferredPublisher == null ? publisher : deferredPublisher);
    }
  }

  @SuppressWarnings("unused")
  public static class GenerateRequestIdAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Return String requestId) {
      McpToolCallState state = currentState().get();
      if (state != null) {
        state.captureRequestId(requestId);
      }
    }
  }
}

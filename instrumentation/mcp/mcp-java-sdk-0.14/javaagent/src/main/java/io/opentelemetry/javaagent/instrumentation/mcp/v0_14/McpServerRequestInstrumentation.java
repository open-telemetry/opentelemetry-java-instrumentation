/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Mono;

class McpServerRequestInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.modelcontextprotocol.spec.McpServerSession",
        "io.modelcontextprotocol.spec.McpStreamableServerSession",
        "io.modelcontextprotocol.server.DefaultMcpStatelessServerHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleIncomingRequest")
            .and(takesArgument(0, named("io.modelcontextprotocol.spec.McpSchema$JSONRPCRequest")))
            .and(takesArgument(1, named("io.modelcontextprotocol.common.McpTransportContext")))
            .and(returns(named("reactor.core.publisher.Mono"))),
        getClass().getName() + "$StatefulRequestAdvice");
    transformer.applyAdviceToMethod(
        named("responseStream")
            .and(takesArgument(0, named("io.modelcontextprotocol.spec.McpSchema$JSONRPCRequest")))
            .and(
                takesArgument(
                    1, named("io.modelcontextprotocol.spec.McpStreamableServerTransport")))
            .and(returns(named("reactor.core.publisher.Mono"))),
        getClass().getName() + "$StreamableRequestAdvice");
    transformer.applyAdviceToMethod(
        named("handleRequest")
            .and(takesArgument(0, named("io.modelcontextprotocol.common.McpTransportContext")))
            .and(takesArgument(1, named("io.modelcontextprotocol.spec.McpSchema$JSONRPCRequest")))
            .and(returns(named("reactor.core.publisher.Mono"))),
        getClass().getName() + "$StatelessRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class StatefulRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static McpServerToolCallState onEnter(
        @Advice.This McpServerSession session,
        @Advice.Argument(0) McpSchema.JSONRPCRequest request) {
      return McpServerToolCallState.prepare(request, session.getId());
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Mono<?> onExit(
        @Advice.Return @Nullable Mono<?> publisher,
        @Advice.Enter @Nullable McpServerToolCallState state) {
      return state == null ? publisher : state.finish(publisher);
    }
  }

  @SuppressWarnings("unused")
  public static class StreamableRequestAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.This McpStreamableServerSession session,
        @Advice.Argument(0) McpSchema.JSONRPCRequest request,
        @Advice.Argument(1) McpStreamableServerTransport transport) {
      McpServerToolCallState state = McpServerToolCallState.prepare(request, session.getId());
      return new Object[] {state == null ? transport : state.wrap(transport), state};
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Mono<?> onExit(
        @Advice.Return @Nullable Mono<?> publisher, @Advice.Enter @Nullable Object[] enterResult) {
      if (enterResult == null) {
        return publisher;
      }
      McpServerToolCallState state = (McpServerToolCallState) enterResult[1];
      return state == null ? publisher : state.finish(publisher);
    }
  }

  @SuppressWarnings("unused")
  public static class StatelessRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static McpServerToolCallState onEnter(
        @Advice.Argument(1) McpSchema.JSONRPCRequest request) {
      return McpServerToolCallState.prepare(request, null);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Mono<?> onExit(
        @Advice.Return @Nullable Mono<?> publisher,
        @Advice.Enter @Nullable McpServerToolCallState state) {
      return state == null ? publisher : state.finish(publisher);
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.modelcontextprotocol.spec.McpClientSession;
import io.modelcontextprotocol.spec.McpSchema;
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
  }

  @SuppressWarnings("unused")
  public static class SendRequestAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Nullable
    public static Mono<?> onExit(
        @Advice.This McpClientSession session,
        @Advice.Argument(0) String method,
        @Advice.Argument(1) @Nullable Object requestParameters,
        @Advice.Return @Nullable Mono<?> publisher) {
      if (publisher != null
          && McpSchema.METHOD_TOOLS_CALL.equals(method)
          && requestParameters instanceof McpSchema.CallToolRequest callToolRequest) {
        return McpToolCallMono.wrap(session, callToolRequest, publisher);
      }
      return McpProtocolVersionState.capture(session, method, publisher);
    }
  }
}

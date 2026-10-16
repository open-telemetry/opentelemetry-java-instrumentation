/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class McpStreamableServerSessionFactoryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.modelcontextprotocol.spec.DefaultMcpStreamableServerSessionFactory");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("startSession")
            .and(
                takesArgument(0, named("io.modelcontextprotocol.spec.McpSchema$InitializeRequest")))
            .and(
                returns(
                    named(
                        "io.modelcontextprotocol.spec.McpStreamableServerSession$McpStreamableServerSessionInit"))),
        getClass().getName() + "$StartSessionAdvice");
  }

  @SuppressWarnings("unused")
  public static class StartSessionAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static McpStreamableServerSession.McpStreamableServerSessionInit onExit(
        @Advice.Return @Nullable
            McpStreamableServerSession.McpStreamableServerSessionInit sessionInit) {
      return McpProtocolVersionState.capture(sessionInit);
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.modelcontextprotocol.spec.McpSchema;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class McpTransportInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.modelcontextprotocol.spec.McpTransport");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.modelcontextprotocol.spec.McpTransport"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("sendMessage")
            .and(takesArguments(named("io.modelcontextprotocol.spec.McpSchema$JSONRPCMessage"))),
        getClass().getName() + "$SendMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendMessageAdvice {
    @AssignReturned.ToArguments(@AssignReturned.ToArguments.ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static McpSchema.JSONRPCMessage onEnter(
        @Advice.Argument(0) McpSchema.JSONRPCMessage message) {
      return McpToolCallState.inject(message);
    }
  }
}

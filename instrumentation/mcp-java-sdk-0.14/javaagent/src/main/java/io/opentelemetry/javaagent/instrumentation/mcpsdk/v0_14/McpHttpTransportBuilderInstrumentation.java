/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.modelcontextprotocol.client.transport.customizer.McpAsyncHttpClientRequestCustomizer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class McpHttpTransportBuilderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport$Builder",
        "io.modelcontextprotocol.client.transport.HttpClientSseClientTransport$Builder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // before 2.1.0
    transformer.applyAdviceToMethod(
        named("build").and(takesArguments(0)).and(hasDeclaredField("httpRequestCustomizer")),
        getClass().getName() + "$BuildAdvice");
    // since 2.1.0
    transformer.applyAdviceToMethod(
        named("httpRequestCustomizer")
            .and(
                returns(
                    named(
                        "io.modelcontextprotocol.client.transport.customizer.McpAsyncHttpClientRequestCustomizer"))),
        getClass().getName() + "$CustomizerAdvice");
  }

  private static ElementMatcher<MethodDescription> hasDeclaredField(String fieldName) {
    return target ->
        target.getDeclaringType().getDeclaredFields().stream()
            .anyMatch(field -> fieldName.equals(field.getName()));
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {
    @AssignReturned.ToFields(@AssignReturned.ToFields.ToField("httpRequestCustomizer"))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static McpAsyncHttpClientRequestCustomizer onEnter(
        @Advice.FieldValue("httpRequestCustomizer") @Nullable
            McpAsyncHttpClientRequestCustomizer customizer) {
      return McpHttpRequestCustomizer.wrap(customizer);
    }
  }

  @SuppressWarnings("unused")
  public static class CustomizerAdvice {
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static McpAsyncHttpClientRequestCustomizer onExit(
        @Advice.Return McpAsyncHttpClientRequestCustomizer customizer) {
      return McpHttpRequestCustomizer.wrap(customizer);
    }
  }
}

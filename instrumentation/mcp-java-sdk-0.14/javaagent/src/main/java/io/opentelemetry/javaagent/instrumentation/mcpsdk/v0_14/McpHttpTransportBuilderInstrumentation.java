/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcpsdk.v0_14;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.modelcontextprotocol.client.transport.customizer.McpAsyncHttpClientRequestCustomizer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
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
    transformer.applyAdviceToMethod(
        named("build").and(takesArguments(0)), getClass().getName() + "$BuildAdvice");
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
}

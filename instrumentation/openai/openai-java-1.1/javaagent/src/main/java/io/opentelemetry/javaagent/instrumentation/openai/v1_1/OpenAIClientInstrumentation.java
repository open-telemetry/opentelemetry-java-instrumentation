/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openai.v1_1;

import static io.opentelemetry.javaagent.instrumentation.openai.v1_1.OpenAISingletons.TELEMETRY;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.openai.client.OpenAIClient;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@SuppressWarnings("IdentifierName") // Want to match library's convention
public class OpenAIClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.openai.client.okhttp.OpenAIOkHttpClient$Builder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("build").and(returns(named("com.openai.client.OpenAIClient"))),
        OpenAIClientInstrumentation.class.getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.AssignReturned.ToReturned
    public static OpenAIClient onExit(@Advice.Return OpenAIClient client) {
      return TELEMETRY.wrap(client);
    }
  }
}

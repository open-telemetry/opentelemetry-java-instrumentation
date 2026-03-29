/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;

public class DefaultBedrockRuntimeAsyncClientBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(
        "software.amazon.awssdk.services.bedrockruntime.DefaultBedrockRuntimeAsyncClientBuilder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("buildClient"), this.getClass().getName() + "$BuildClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildClientAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static BedrockRuntimeAsyncClient methodExit(
        @Advice.Return BedrockRuntimeAsyncClient client) {
      return AwsSdkSingletons.telemetry().wrapBedrockRuntimeClient(client);
    }
  }
}

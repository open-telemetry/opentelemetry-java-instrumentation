/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.instrumentationproperties;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.AgentInitializer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.URL;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class EmbeddedPropertiesLoaderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(
        "io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties$EmbeddedPropertiesLoader");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getResource").and(takesArguments(String.class)).and(returns(URL.class)),
        this.getClass().getName() + "$GetResourceAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetResourceAdvice {

    // skip the original method body and replace it completely
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static boolean onEnter() {
      return false;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) String path, @Advice.Return(readOnly = false) URL resource) {
      // load the instrumentation properties from the extension class loader instead
      resource = AgentInitializer.getExtensionsClassLoader().getResource(path);
    }
  }
}

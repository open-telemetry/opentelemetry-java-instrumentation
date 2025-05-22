/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v5_0.client;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.resource.ResourceManager;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HttpClientImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.http.impl.HttpClientImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), this.getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.FieldValue("httpCM") ResourceManager<?, ?> resourceManager,
        @Advice.FieldValue("vertx") VertxInternal vertx) {
      VertxClientSingletons.setVertx(resourceManager, vertx);
    }
  }
}

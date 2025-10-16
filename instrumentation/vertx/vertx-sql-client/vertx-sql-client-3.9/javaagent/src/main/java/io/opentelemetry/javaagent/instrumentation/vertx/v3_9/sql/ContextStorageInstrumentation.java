/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.sql;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Vertx;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ContextStorageInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.http.impl.HttpServerRequestImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleBegin"), 
        ContextStorageInstrumentation.class.getName() + "$StoreContextAdvice");
  }

  @SuppressWarnings("unused")
  public static class StoreContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      // Store current OpenTelemetry context in Vert.x context
      io.vertx.core.Context vertxContext = Vertx.currentContext();
      if (vertxContext != null) {
        Context otelContext = Context.current();
//        vertxContext.put("otel.context", otelContext);
//        System.out.println("DEBUG: Stored OTel context in Vert.x context: " + otelContext);
      }
    }
  }
}

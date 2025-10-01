/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.universal;

import static net.bytebuddy.matcher.ElementMatchers.named;

//import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Vertx;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumentation that stores OpenTelemetry context in Vertx context during HTTP request
 * processing.
 *
 * <p>This bridges the gap between Netty HTTP server instrumentation and Vertx operations by
 * capturing the current OpenTelemetry context when HTTP requests begin processing and storing it in
 * the Vertx context for downstream operations to access.
 *
 * <p>Based on the same pattern used in vertx-sql-client instrumentation.
 */
public class VertxContextStorageInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.http.impl.HttpServerRequestImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handleBegin"),
        VertxContextStorageInstrumentation.class.getName() + "$StoreContextAdvice");
  }

  @SuppressWarnings("unused")
  public static class StoreContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      // Store current OpenTelemetry context in Vertx context for downstream operations
         io.vertx.core.Context vertxContext = Vertx.currentContext();
          Context otelContext = Context.current();
         if (vertxContext != null&&(otelContext!=null&&otelContext!=Context.root())) {

           // Create a unique key for this request to avoid conflicts between concurrent requests
//      String contextKey =
//          "otel.context." + Thread.currentThread().getId() + "." + System.nanoTime();

           vertxContext.put("otel.context", otelContext);
         }

      // Store the context and the key (following the same pattern as SQL instrumentation)
      // vertxContext.put(contextKey, otelContext);
      // vertxContext.put("otel.current.context.key", contextKey);

//      Span currentSpan = Span.fromContext(otelContext);
//      System.out.println(
//          "[VERTX-CONTEXT-STORAGE-NEW] Stored OTel context in Vertx: "
//              + "OtelContext="
//              + otelContext
//              + ", \nTraceId="
//              + currentSpan.getSpanContext().getTraceId()
//              + ", \nSpanId="
//              + currentSpan.getSpanContext().getSpanId()
////              + ", \nKey="
////              + contextKey
//              + ", \nThread="
//              + Thread.currentThread().getName());
      //   } else {
      // System.out.println("[VERTX-CONTEXT-STORAGE] No Vertx context available for storage");
      //   }
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cfml;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import javax.annotation.Nullable;

public class CfmlUDFMethodInstrumentationSingletons {
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.cfml.experimental-span-attributes", false);

  private static final Instrumenter<Object, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<JspCompilationContext, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.cfml",
                CfmlUDFMethodInstrumentationSingletons::spanNameOnCompile)
            .addAttributesExtractor(new CompilationAttributesExtractor())
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static String spanNameOnCompile(Object obj) {
    return "Compile ";
  }

  public static Instrumenter<Object, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private CfmlCompilationContextInstrumentationSingletons() {}

  private static class CompilationAttributesExtractor
      implements AttributesExtractor<Object, Void> {

    @Override
    public void onStart(
        AttributesBuilder attributes,
        Context parentContext,
        Object obj) {}

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        Object obj,
        @Nullable Void unused,
        @Nullable Throwable error) {
      if (!CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
        return;
      }

      // Compiler compiler = jspCompilationContext.getCompiler();
      // if (compiler != null) {
      //   attributes.put("jsp.compiler", compiler.getClass().getName());
      // }
      // attributes.put("jsp.classFQCN", jspCompilationContext.getFQCN());
    }
  }
}

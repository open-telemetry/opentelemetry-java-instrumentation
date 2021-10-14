/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.compiler.Compiler;
import org.checkerframework.checker.nullness.qual.Nullable;

public class JspCompilationContextInstrumentationSingletons {
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.jsp.experimental-span-attributes", false);

  private static final Instrumenter<JspCompilationContext, Void> INSTRUMENTER;

  static {
    INSTRUMENTER =
        Instrumenter.<JspCompilationContext, Void>newBuilder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.jsp-2.3",
                JspCompilationContextInstrumentationSingletons::spanNameOnCompile)
            .addAttributesExtractor(new CompilationAttributesExtractor())
            .newInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static String spanNameOnCompile(JspCompilationContext jspCompilationContext) {
    return "Compile " + jspCompilationContext.getJspFile();
  }

  public static Instrumenter<JspCompilationContext, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private JspCompilationContextInstrumentationSingletons() {}

  private static class CompilationAttributesExtractor
      implements AttributesExtractor<JspCompilationContext, Void> {

    @Override
    public void onStart(
        AttributesBuilder attributes, JspCompilationContext jspCompilationContext) {}

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        JspCompilationContext jspCompilationContext,
        @Nullable Void unused,
        @Nullable Throwable error) {
      if (!CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
        return;
      }

      Compiler compiler = jspCompilationContext.getCompiler();
      if (compiler != null) {
        attributes.put("jsp.compiler", compiler.getClass().getName());
      }
      attributes.put("jsp.classFQCN", jspCompilationContext.getFQCN());
    }
  }
}

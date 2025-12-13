/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jfinal.v3_2;

import com.jfinal.core.Action;
import com.jfinal.render.JsonRender;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;

public final class JFinalSingletons {

  private static final Instrumenter<ClassAndMethod, Void> INSTRUMENTER;

  static {
    // see
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/11465#issuecomment-2137294837
    excludeOtAttrs();

    CodeAttributesGetter<ClassAndMethod> codedAttributesGetter =
        ClassAndMethod.codeAttributesGetter();
    INSTRUMENTER =
        Instrumenter.<ClassAndMethod, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.jfinal-3.2",
                CodeSpanNameExtractor.create(codedAttributesGetter))
            .setEnabled(
                DeclarativeConfigUtil.getBoolean(
                        GlobalOpenTelemetry.get(),
                        "java",
                        "common",
                        "controller_telemetry/development",
                        "enabled")
                    .orElse(false))
            .addAttributesExtractor(CodeAttributesExtractor.create(codedAttributesGetter))
            .buildInstrumenter();
  }

  public static Instrumenter<ClassAndMethod, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static void updateRoute(Action action) {
    if (action == null) {
      return;
    }
    String route = action.getActionKey();
    Context context = Context.current();
    if (route != null) {
      HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, route);
    }
  }

  private static void excludeOtAttrs() {
    JsonRender.addExcludedAttrs(
        "io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper.Context",
        "trace_id",
        "span_id");
  }

  private JFinalSingletons() {}
}

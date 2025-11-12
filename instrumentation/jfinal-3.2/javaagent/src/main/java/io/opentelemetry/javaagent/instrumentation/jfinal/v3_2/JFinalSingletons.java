/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jfinal.v3_2;

import com.jfinal.core.Action;
import com.jfinal.core.Controller;
import com.jfinal.render.JsonRender;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JFinalSingletons {

  private static final Logger logger = Logger.getLogger(JFinalSingletons.class.getName());

  private static final String SPAN_NAME = "jfinal.handle";
  private static final Instrumenter<Void, Void> INSTRUMENTER =
      Instrumenter.<Void, Void>builder(
              GlobalOpenTelemetry.get(), "io.opentelemetry.jfinal-3.2", s -> SPAN_NAME)
          .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
          .buildInstrumenter();

  static {
    // see
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/11465#issuecomment-2137294837
    excludeOtAttrs();
  }

  public static Instrumenter<Void, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static void updateSpan(Action action) {
    if (action == null) {
      return;
    }
    String route = action.getActionKey();
    Context context = Context.current();
    if (route != null) {
      HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, route);
    }
    Class<? extends Controller> clazz = action.getControllerClass();
    Method method = action.getMethod();
    if (clazz != null && method != null) {
      Span.fromContext(context).updateName(clazz.getSimpleName() + '.' + method.getName());
    }
  }

  private static void excludeOtAttrs() {
    try {
      JsonRender.addExcludedAttrs(
          "io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper.Context",
          "trace_id",
          "span_id");
    } catch (Throwable t) {
      logger.log(Level.INFO, "exclude failed", t);
    }
  }

  private JFinalSingletons() {}
}

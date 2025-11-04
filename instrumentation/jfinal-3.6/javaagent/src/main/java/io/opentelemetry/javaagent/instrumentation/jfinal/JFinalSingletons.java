package io.opentelemetry.javaagent.instrumentation.jfinal;

import com.jfinal.core.Action;
import com.jfinal.render.JsonRender;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JFinalSingletons {

  private static final Logger logger = Logger.getLogger(JFinalSingletons.class.getName());

  private static final String SPAN_NAME = "jfinal.handle";
  private static final Instrumenter<Void, Void> INSTRUMENTER =
      Instrumenter.<Void, Void>builder(
              GlobalOpenTelemetry.get(), "io.opentelemetry.jfinal-3.6", s -> SPAN_NAME)
          .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
          .buildInstrumenter();

  static {
    //see https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/11465
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
    if (route == null) {
      return;
    }
    Context context = Context.current();
    HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, route);
  }

  private static void excludeOtAttrs() {
    try {
      JsonRender.addExcludedAttrs(
          "io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper.AsyncListenerResponse",
          "io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper.Context",
          "trace_id",
          "span_id");
    } catch (Throwable t) {
      logger.log(Level.INFO, "exclude failed", t);
    }
  }

  private JFinalSingletons() {}
}

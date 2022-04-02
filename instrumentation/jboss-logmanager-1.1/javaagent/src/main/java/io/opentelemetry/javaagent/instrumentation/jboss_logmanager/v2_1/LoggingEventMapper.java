package io.opentelemetry.javaagent.instrumentation.jboss_logmanager.v2_1;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.appender.internal.LogBuilder;
import io.opentelemetry.instrumentation.api.appender.internal.Severity;
import io.opentelemetry.instrumentation.api.cache.Cache;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.instrumentation.api.appender.internal.AgentLogEmitterProvider;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.MDC;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LoggingEventMapper {

  public static final LoggingEventMapper INSTANCE = new LoggingEventMapper();

  private static final Cache<String, AttributeKey<String>> mdcAttributeKeys = Cache.bounded(100);

  private final List<String> captureMdcAttributes;

  private static final boolean captureExperimentalAttributes =
      Config.get()
          .getBoolean("otel.instrumentation.jboss-logmanager.experimental-log-attributes", false);

  // cached as an optimization
  private final boolean captureAllMdcAttributes;

  private LoggingEventMapper() {
    this.captureMdcAttributes =
        Config.get()
            .getList(
                "otel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes",
                emptyList());
    this.captureAllMdcAttributes =
        captureMdcAttributes.size() == 1 && captureMdcAttributes.get(0).equals("*");
  }

  public void capture(Logger logger, ExtLogRecord record) {
    String instrumentationName = logger.getName();
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      instrumentationName = "ROOT";
    }

    LogBuilder builder =
        AgentLogEmitterProvider.get().logEmitterBuilder(instrumentationName).build().logBuilder();

    String message = record.getFormattedMessage();
    if (message != null) {
      builder.setBody(message);
    }

    java.util.logging.Level level = record.getLevel();
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.toString());
    }

    AttributesBuilder attributes = Attributes.builder();

    Throwable throwable = record.getThrown();
    if (throwable != null) {
      // TODO (trask) extract method for recording exception into
      // instrumentation-appender-api-internal
      attributes.put(SemanticAttributes.EXCEPTION_TYPE, throwable.getClass().getName());
      attributes.put(SemanticAttributes.EXCEPTION_MESSAGE, throwable.getMessage());
      StringWriter writer = new StringWriter();
      throwable.printStackTrace(new PrintWriter(writer));
      attributes.put(SemanticAttributes.EXCEPTION_STACKTRACE, writer.toString());
    }
    captureMdcAttributes(attributes);

    if (captureExperimentalAttributes) {
      Thread currentThread = Thread.currentThread();
      attributes.put(SemanticAttributes.THREAD_NAME, currentThread.getName());
      attributes.put(SemanticAttributes.THREAD_ID, currentThread.getId());
    }

    builder.setAttributes(attributes.build());

    builder.setContext(Context.current());

    builder.emit();
  }

  private void captureMdcAttributes(AttributesBuilder attributes) {

    Map<String, String> context = MDC.copy();

    if (captureAllMdcAttributes) {
      if (context != null) {
        for (Map.Entry<String, String> entry : context.entrySet()) {
          attributes.put(
              getMdcAttributeKey(String.valueOf(entry.getKey())), String.valueOf(entry.getValue()));
        }
      }
      return;
    }

    for (String key : captureMdcAttributes) {
      Object value = context.get(key);
      if (value != null) {
        attributes.put(key, value.toString());
      }
    }
  }

  public static AttributeKey<String> getMdcAttributeKey(String key) {
    return mdcAttributeKeys.computeIfAbsent(
        key, k -> AttributeKey.stringKey("jboss-logmanager.mdc." + k));
  }

  private static Severity levelToSeverity(java.util.logging.Level level) {
    int levelInt = level.intValue();
    if (levelInt >= Level.FATAL.intValue()) {
      return Severity.FATAL;
    } else if (levelInt >= Level.ERROR.intValue()) {
      return Severity.ERROR;
    } else if (levelInt >= Level.WARNING.intValue()) {
      return Severity.WARN;
    } else if (levelInt >= Level.INFO.intValue()) {
      return Severity.INFO;
    } else if (levelInt >= Level.DEBUG.intValue()) {
      return Severity.DEBUG;
    } else if (levelInt >= Level.TRACE.intValue()) {
      return Severity.TRACE;
    }
    return Severity.UNDEFINED_SEVERITY_NUMBER;
  }
}

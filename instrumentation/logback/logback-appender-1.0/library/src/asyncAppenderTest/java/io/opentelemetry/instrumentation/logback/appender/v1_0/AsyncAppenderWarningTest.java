/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import ch.qos.logback.core.status.Status;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;
import org.slf4j.spi.MDCAdapter;

class AsyncAppenderWarningTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  private final LoggerContext loggerContext = new LoggerContext();

  @BeforeEach
  void setUp() throws ReflectiveOperationException {
    // Newer Logback versions initialize the MDC adapter through their SLF4J service provider.
    try {
      LoggerContext.class
          .getMethod("setMDCAdapter", MDCAdapter.class)
          .invoke(loggerContext, MDC.getMDCAdapter());
    } catch (NoSuchMethodException ignored) {
      // Logback 1.0 uses the static MDC adapter instead.
    }
  }

  @AfterEach
  void tearDown() {
    loggerContext.stop();
  }

  @Test
  void xmlConfigurationWarnsAtSdkInstallationOnlyOnce() throws JoranException {
    OpenTelemetryAppender appender = configureXml();
    assertThat(warnings()).isEmpty();

    appender.setOpenTelemetry(testing.getOpenTelemetry());
    assertWarning(appender, "ASYNC");

    appender.setOpenTelemetry(testing.getOpenTelemetry());
    assertThat(warnings()).hasSize(1);
  }

  @Test
  void findsAsyncBoundaryThroughSynchronousWrappersAndNestedAsyncAppenders() {
    OpenTelemetryAppender appender = createAppender("NESTED_OTEL");
    appender.start();
    AsyncAppender inner = async("INNER_ASYNC", wrapper(appender));
    AsyncAppender outer = async("OUTER_ASYNC", wrapper(inner));
    loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(wrapper(outer));

    appender.setOpenTelemetry(testing.getOpenTelemetry());

    assertWarning(appender, "INNER_ASYNC");
  }

  @Test
  void directAppenderWithSeparateAsyncOutputDoesNotWarnAndPreservesCorrelation() {
    ListAppender<ILoggingEvent> console = new ListAppender<>();
    console.setContext(loggerContext);
    console.start();
    Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    AsyncAppender asyncConsole = async("ASYNC_CONSOLE", console);
    root.addAppender(asyncConsole);
    asyncConsole.start();
    OpenTelemetryAppender appender = createAppender("DIRECT_OTEL");
    root.addAppender(appender);
    appender.start();
    appender.setOpenTelemetry(testing.getOpenTelemetry());

    Span span = testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
    try (Scope ignored = span.makeCurrent()) {
      root.info("inside span");
    } finally {
      span.end();
    }
    root.info("outside span");
    asyncConsole.stop();

    testing.waitAndAssertLogRecords(
        log -> log.hasBody("inside span").hasSpanContext(span.getSpanContext()),
        log -> log.hasBody("outside span").hasSpanContext(SpanContext.getInvalid()));
    assertThat(console.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly("inside span", "outside span");
    assertThat(warnings()).isEmpty();
  }

  private OpenTelemetryAppender configureXml() throws JoranException {
    String configuration =
        "<configuration>"
            + "<appender name='OTEL' class='"
            + OpenTelemetryAppender.class.getName()
            + "'/>"
            + "<appender name='ASYNC' class='ch.qos.logback.classic.AsyncAppender'>"
            + "<appender-ref ref='OTEL'/></appender>"
            + "<root level='INFO'><appender-ref ref='ASYNC'/></root>"
            + "</configuration>";
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(loggerContext);
    configurator.doConfigure(new ByteArrayInputStream(configuration.getBytes(UTF_8)));
    AsyncAppender async =
        (AsyncAppender) loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("ASYNC");
    return (OpenTelemetryAppender) async.getAppender("OTEL");
  }

  private OpenTelemetryAppender createAppender(String name) {
    OpenTelemetryAppender appender = new OpenTelemetryAppender();
    appender.setContext(loggerContext);
    appender.setName(name);
    return appender;
  }

  private AsyncAppender async(String name, Appender<ILoggingEvent> delegate) {
    AsyncAppender appender = new AsyncAppender();
    appender.setContext(loggerContext);
    appender.setName(name);
    appender.addAppender(delegate);
    return appender;
  }

  private AttachingAppender wrapper(Appender<ILoggingEvent> delegate) {
    AttachingAppender appender = new AttachingAppender();
    appender.setContext(loggerContext);
    appender.setName("WRAPPER");
    appender.addAppender(delegate);
    return appender;
  }

  private List<Status> warnings() {
    return loggerContext.getStatusManager().getCopyOfStatusList().stream()
        .filter(status -> status.getLevel() == Status.WARN)
        .filter(status -> status.getMessage().contains("is downstream of AsyncAppender"))
        .collect(toList());
  }

  private void assertWarning(OpenTelemetryAppender appender, String asyncName) {
    assertThat(warnings())
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.getOrigin()).isSameAs(appender);
              assertThat(warning.getMessage())
                  .contains(
                      "OpenTelemetryAppender '"
                          + appender.getName()
                          + "' is downstream of AsyncAppender '"
                          + asyncName
                          + "'",
                      "trace/span correlation",
                      "SDK batch export")
                  .containsIgnoringCase("attach OpenTelemetryAppender directly");
            });
  }

  private static class AttachingAppender extends AppenderBase<ILoggingEvent>
      implements AppenderAttachable<ILoggingEvent> {
    private final AppenderAttachableImpl<ILoggingEvent> appenders = new AppenderAttachableImpl<>();

    @Override
    protected void append(ILoggingEvent event) {}

    @Override
    public void addAppender(Appender<ILoggingEvent> appender) {
      appenders.addAppender(appender);
    }

    @Override
    public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
      return appenders.iteratorForAppenders();
    }

    @Override
    public Appender<ILoggingEvent> getAppender(String name) {
      return appenders.getAppender(name);
    }

    @Override
    public boolean isAttached(Appender<ILoggingEvent> appender) {
      return appenders.isAttached(appender);
    }

    @Override
    public void detachAndStopAllAppenders() {
      appenders.detachAndStopAllAppenders();
    }

    @Override
    public boolean detachAppender(Appender<ILoggingEvent> appender) {
      return appenders.detachAppender(appender);
    }

    @Override
    public boolean detachAppender(String name) {
      return appenders.detachAppender(name);
    }
  }
}

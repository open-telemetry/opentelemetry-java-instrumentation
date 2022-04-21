package io.opentelemetry.javaagent.slf4j.impl;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.util.Loader;
import org.slf4j.ILoggerFactory;
import org.slf4j.helpers.Util;
import org.slf4j.spi.LoggerFactoryBinder;
import java.net.URL;

/**
 * The slf4j-api would try to load org.slf4j.impl.StaticLoggerBinder internal. In the agent core,
 * we add our own implementation for bridging to Otel internal log component. Therefore, logs of
 * grpc/kafka(agent shaded components) would output through the Otel's log. Don't move this class
 * to any other package, its package must be as same as the shaded io.opentelemetry.javaagent.slf4j.impl
 */
public class StaticLoggerBinder implements LoggerFactoryBinder {

  private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();
  private final LoggerContext defaultLoggerContext = new LoggerContext();

  public final static String OTEL_FILE = "logback-otel.xml";

  static {
    SINGLETON.init();
  }

  @Override
  public ILoggerFactory getLoggerFactory() {
    return defaultLoggerContext;
  }

  @Override
  public String getLoggerFactoryClassStr() {
    return defaultLoggerContext.getClass().getName();
  }

  private void init() {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try {
      URL url = Loader.getResource(OTEL_FILE, classLoader);
      new ContextInitializer(defaultLoggerContext).configureByResource(url);
    } catch (Exception e) {
      Util.report("Failed to otel instantiate [" + LoggerContext.class.getName() + "]", e);
    }
  }

  public static StaticLoggerBinder getSingleton() {
    return SINGLETON;
  }
}

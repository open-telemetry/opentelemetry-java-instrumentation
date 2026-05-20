/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.engine;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.instrumentation.jmx.JmxMetricHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;

/**
 * Collecting and exporting JMX metrics.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class JmxMetricInsight {

  private static final Logger logger = Logger.getLogger(JmxMetricInsight.class.getName());

  private static final String INSTRUMENTATION_SCOPE = "io.opentelemetry.jmx";

  private static final ComponentLoader defaultComponentLoader =
      ComponentLoader.forClassLoader(JmxMetricInsight.class.getClassLoader());

  private final OpenTelemetry openTelemetry;
  private final long discoveryDelay;

  public static JmxMetricInsight createService(OpenTelemetry ot, long discoveryDelay) {
    return new JmxMetricInsight(ot, discoveryDelay);
  }

  private JmxMetricInsight(OpenTelemetry openTelemetry, long discoveryDelay) {
    this.openTelemetry = openTelemetry;
    this.discoveryDelay = discoveryDelay;
  }

  /**
   * Starts metric registration for local JVM
   *
   * @param conf metric configuration
   */
  public AutoCloseable startLocal(MetricConfiguration conf) {
    return start(conf, () -> MBeanServerFactory.findMBeanServer(null), defaultComponentLoader);
  }

  /**
   * Starts metric registration for a remote JVM connection
   *
   * @param conf metric configuration
   * @param connections supplier for list of remote connections
   */
  @SuppressWarnings("unused") // used by jmx-scraper with remote connection
  public AutoCloseable startRemote(
      MetricConfiguration conf, Supplier<List<? extends MBeanServerConnection>> connections) {
    return start(conf, connections, defaultComponentLoader);
  }

  /**
   * Starts metric registration on the provided list of connections
   *
   * @param conf metric configuration
   * @param connections supplier for list of connections (remote or local)
   */
  public AutoCloseable start(
      MetricConfiguration conf,
      Supplier<List<? extends MBeanServerConnection>> connections,
      ComponentLoader componentLoader) {
    if (conf.isEmpty()) {
      logger.log(
          FINE,
          "Empty JMX configuration, no metrics will be collected for InstrumentationScope "
              + INSTRUMENTATION_SCOPE);
      return () -> {};
    } else {
      Map<String, JmxMetricHandler> handlers = new HashMap<>();
      for (JmxMetricHandler handler : componentLoader.load(JmxMetricHandler.class)) {
        String name = handler.getName();
        if (handlers.putIfAbsent(name, handler) != null) {
          logger.warning(
              "Multiple JmxMetricHandlers with the same name found: "
                  + name
                  + ". Only one will be used.");
        }
      }

      MetricRegistrar registrar = new MetricRegistrar(openTelemetry, INSTRUMENTATION_SCOPE);
      BeanFinder finder = new BeanFinder(conf, registrar, handlers, discoveryDelay);
      finder.discoverBeans(connections);

      return () -> {
        try {
          finder.shutdown();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw e;
        } finally {
          registrar.close();
        }
      };
    }
  }
}

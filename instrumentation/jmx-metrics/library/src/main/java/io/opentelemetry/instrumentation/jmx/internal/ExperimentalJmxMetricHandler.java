/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal;

import io.opentelemetry.api.metrics.Meter;
import java.util.Collection;
import java.util.function.Supplier;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/**
 * A service provider interface (SPI) for producing custom JMX based metrics.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface ExperimentalJmxMetricHandler {

  /**
   * Create instruments for produced metrics.
   *
   * @param meter the {@link Meter} to use for creating instruments
   * @param detectorSupplier a supplier of {@link Detector} that provides access to
   *     MBeanServerConnection and ObjectName for the MBeans of interest
   * @return a {@link AutoCloseable} for cleaning up resources when the metric collection is stopped
   */
  AutoCloseable create(Meter meter, Supplier<Detector> detectorSupplier);

  /**
   * Name of the handler that can be used in yaml configuration to specify which handlers to use.
   */
  String getName();

  /**
   * A helper class that provides access to MBeanServerConnection and ObjectName for the MBeans of
   * interest.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  interface Detector {
    /** Get the MBeanServerConnection to query MBeans. */
    MBeanServerConnection getConnection();

    /** Get the ObjectNames of the MBeans to query. */
    Collection<ObjectName> getObjectNames();
  }
}

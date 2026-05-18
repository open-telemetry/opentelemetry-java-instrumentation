/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import io.opentelemetry.api.metrics.Meter;
import java.util.Collection;
import java.util.function.Supplier;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public interface JmxMetricHandler {
  AutoCloseable create(Meter meter, Supplier<Detector> detectorSupplier);

  String getName();

  interface Detector {
    MBeanServerConnection getConnection();

    Collection<ObjectName> getObjectNames();
  }
}

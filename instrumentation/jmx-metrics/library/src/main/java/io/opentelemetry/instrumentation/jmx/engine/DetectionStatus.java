/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import java.util.Collection;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/**
 * A class encapsulating a set of ObjectNames and the MBeanServer that recognized them. Objects of
 * this class are immutable.
 */
class DetectionStatus {

  private final MBeanServerConnection connection;
  private final Collection<ObjectName> objectNames;

  DetectionStatus(MBeanServerConnection connection, Collection<ObjectName> objectNames) {
    this.connection = connection;
    this.objectNames = objectNames;
  }

  MBeanServerConnection getConnection() {
    return connection;
  }

  Collection<ObjectName> getObjectNames() {
    return objectNames;
  }
}

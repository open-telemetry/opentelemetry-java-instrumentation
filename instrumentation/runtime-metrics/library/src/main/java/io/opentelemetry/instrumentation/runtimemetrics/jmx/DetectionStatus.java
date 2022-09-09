/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.jmx;

import java.util.Collection;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * A class encapsulating a set of ObjectNames and the MBeanServer that recognized them. Objects of
 * this class are inmutable.
 */
class DetectionStatus {

  private final MBeanServer server;
  private final Collection<ObjectName> objectNames;

  DetectionStatus(MBeanServer server, Collection<ObjectName> objectNames) {
    this.server = server;
    this.objectNames = objectNames;
  }

  MBeanServer getServer() {
    return server;
  }

  Collection<ObjectName> getObjectNames() {
    return objectNames;
  }
}

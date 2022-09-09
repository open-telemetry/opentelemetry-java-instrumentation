/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.jmx;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * LabelExtractors are responsible for obtaining values for populating Labels, i.e. Measurement
 * attributes.
 */
public interface LabelExtractor {

  String extractValue(MBeanServer server, ObjectName objectName);
}

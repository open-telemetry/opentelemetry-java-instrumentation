/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * MetricAttributeExtractors are responsible for obtaining values for populating metric attributes,
 * i.e. measurement attributes.
 */
public interface MetricAttributeExtractor {

  String extractValue(MBeanServer server, ObjectName objectName);
}

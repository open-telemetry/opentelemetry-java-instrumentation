/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import org.springframework.beans.factory.aot.BeanRegistrationExcludeFilter;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Configures runtime metrics collection for Java 17+.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class RuntimeMetricsBeanRegistrationExcludeFilter implements BeanRegistrationExcludeFilter {
  @Override
  public boolean isExcludedFromAotProcessing(RegisteredBean registeredBean) {
    // GraalVM native image does not support JFR, so we exclude Java 17+ runtime metrics provider
    return "io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics.Java17RuntimeMetricsProvider"
        .equals(registeredBean.getBeanName());
  }
}

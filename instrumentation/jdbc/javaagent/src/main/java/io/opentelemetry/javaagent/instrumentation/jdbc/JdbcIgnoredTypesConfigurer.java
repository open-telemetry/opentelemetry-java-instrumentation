/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

@AutoService(IgnoredTypesConfigurer.class)
public class JdbcIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
    // see https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/5946
    builder.ignoreClass("org.jboss.jca.adapters.jdbc.");
    // see https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/8109
    builder.ignoreClass("org.apache.shardingsphere.shardingjdbc.jdbc.core.statement.");
    // see https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/12065
    builder.ignoreClass("org.apache.shardingsphere.driver.jdbc.core.statement.");
  }
}

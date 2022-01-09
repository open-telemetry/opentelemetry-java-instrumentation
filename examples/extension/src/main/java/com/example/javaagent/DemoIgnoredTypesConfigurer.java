/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;

/**
 * Custom {@link IgnoredTypesConfigurer} which exists currently only to verify correct shading.
 *
 * @see io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer
 */
@AutoService(IgnoredTypesConfigurer.class)
public class DemoIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(Config config, IgnoredTypesBuilder builder) {}
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7

import io.opentelemetry.instrumentation.dubbo.apache.v2_7.api.HelloService
import io.opentelemetry.instrumentation.test.InstrumentationTestTrait
import org.apache.dubbo.config.ReferenceConfig
import org.apache.dubbo.config.ServiceConfig

class DubboTest extends AbstractDubboTest implements InstrumentationTestTrait {
  @Override
  void configureServerFilter(ServiceConfig serviceConfig) {
    serviceConfig.setFilter("OtelServerFilter")
  }

  @Override
  void configureClientFilter(ReferenceConfig<HelloService> referenceConfig) {
    referenceConfig.setFilter("OtelClientFilter")
  }
}

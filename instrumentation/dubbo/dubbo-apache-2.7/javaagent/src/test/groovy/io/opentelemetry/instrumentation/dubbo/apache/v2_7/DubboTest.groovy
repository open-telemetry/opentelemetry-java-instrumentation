/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7

import io.opentelemetry.instrumentation.dubbo.apache.v2_7.api.HelloService
import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.apache.dubbo.config.ReferenceConfig
import org.apache.dubbo.config.ServiceConfig

class DubboTest extends AbstractDubboTest implements AgentTestTrait {
  @Override
  void configureServerFilter(ServiceConfig serviceConfig) {
  }

  @Override
  void configureClientFilter(ReferenceConfig<HelloService> referenceConfig) {
  }
}

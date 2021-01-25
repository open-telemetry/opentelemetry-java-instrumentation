/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7

import io.opentelemetry.instrumentation.dubbo.apache.v2_7.api.HelloService
import io.opentelemetry.instrumentation.dubbo.apache.v2_7.impl.HelloServiceImpl
import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.apache.dubbo.config.ReferenceConfig
import org.apache.dubbo.config.RegistryConfig
import org.apache.dubbo.config.ServiceConfig

class DubboTest extends AbstractDubboTest implements AgentTestTrait {
  @Override
  ServiceConfig configureServer() {
    def registerConfig = new RegistryConfig()
    registerConfig.setAddress("N/A")
    ServiceConfig<HelloServiceImpl> service = new ServiceConfig<>()
    service.setInterface(HelloService)
    service.setRef(new HelloServiceImpl())
    service.setRegistry(registerConfig)
    return service
  }

  @Override
  ReferenceConfig<HelloService> configureClient(int port) {
    ReferenceConfig<HelloService> reference = new ReferenceConfig<>()
    reference.setInterface(HelloService)
    reference.setGeneric("true")
    reference.setUrl("dubbo://localhost:" + port)
    return reference
  }
}

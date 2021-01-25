/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7


import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.dubbo.config.ApplicationConfig
import org.apache.dubbo.config.ProtocolConfig
import org.apache.dubbo.config.ReferenceConfig
import org.apache.dubbo.config.ServiceConfig
import org.apache.dubbo.config.bootstrap.DubboBootstrap
import org.apache.dubbo.config.utils.ReferenceConfigCache
import org.apache.dubbo.rpc.service.GenericService
import spock.lang.Unroll

import static io.opentelemetry.api.trace.Span.Kind.CLIENT
import static io.opentelemetry.api.trace.Span.Kind.SERVER
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

@Unroll
abstract class AbstractDubboTest extends InstrumentationSpecification {

  abstract ServiceConfig configureServer()

  abstract ReferenceConfig<?> configureClient(int port)

  def "test apache dubbo #base"() {
    setup:
    def port = PortUtils.randomOpenPort()
    def protocolConfig = new ProtocolConfig()
    protocolConfig.setPort(port)

    DubboBootstrap bootstrap = DubboBootstrap.getInstance()
    bootstrap.application(new ApplicationConfig("dubbo-test-provider"))
      .service(configureServer())
      .protocol(protocolConfig)
      .start()

    def consumerProtocolConfig = new ProtocolConfig()
    consumerProtocolConfig.setRegister(false)

    def reference = configureClient(port)
    DubboBootstrap consumerBootstrap = DubboBootstrap.getInstance()
    consumerBootstrap.application(new ApplicationConfig("dubbo-demo-api-consumer"))
      .reference(reference)
      .protocol(consumerProtocolConfig)
      .start()

    when:
    GenericService genericService = ReferenceConfigCache.getCache().get(reference) as GenericService
    def o = new Object[1]
    o[0] = "hello"
    def response = runUnderTrace("parent") {
      genericService.$invoke("hello", [String.class.getName()] as String[], o)
    }

    then:
    response == "hello"

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "\$invoke"
          kind CLIENT
          childOf span(0)
          errored false
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "dubbo"
            "${SemanticAttributes.RPC_SERVICE.key}" "GenericService:\$invoke"
            "${SemanticAttributes.RPC_METHOD.key}" "\$invoke"
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
          }
        }
        span(2) {
          name "hello"
          kind SERVER
          childOf span(1)
          errored false
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "dubbo"
            "${SemanticAttributes.RPC_SERVICE.key}" "HelloService:hello"
            "${SemanticAttributes.RPC_METHOD.key}" "hello"
            "${SemanticAttributes.NET_PEER_IP.key}" String
            "${SemanticAttributes.NET_PEER_NAME.key}" { it == null || it instanceof String }
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
          }
        }
      }
    }

    cleanup:
    bootstrap.destroy()
    consumerBootstrap.destroy()
  }
}

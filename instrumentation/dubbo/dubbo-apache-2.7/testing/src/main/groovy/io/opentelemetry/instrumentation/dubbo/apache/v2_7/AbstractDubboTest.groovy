/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.dubbo.apache.v2_7

import io.opentelemetry.instrumentation.dubbo.apache.v2_7.api.HelloService
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

  abstract ReferenceConfig<HelloService> configureClient(int port)

  def "test apache dubbo base #dubbo"() {
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
      genericService.$invoke("hello", [String.getName()] as String[], o)
    }

    and:
    def responseAsync = runUnderTrace("parent") {
      genericService.$invokeAsync("hello", [String.getName()] as String[], o)
    }

    then:
    response == "hello"
    responseAsync.get() == "hello"

    assertTraces(2) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "org.apache.dubbo.rpc.service.GenericService/\$invoke"
          kind CLIENT
          childOf span(0)
          errored false
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "dubbo"
            "${SemanticAttributes.RPC_SERVICE.key}" "org.apache.dubbo.rpc.service.GenericService"
            "${SemanticAttributes.RPC_METHOD.key}" "\$invoke"
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
          }
        }
        span(2) {
          name "io.opentelemetry.instrumentation.dubbo.apache.v2_7.api.HelloService/hello"
          kind SERVER
          childOf span(1)
          errored false
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "dubbo"
            "${SemanticAttributes.RPC_SERVICE.key}" "io.opentelemetry.instrumentation.dubbo.apache.v2_7.api.HelloService"
            "${SemanticAttributes.RPC_METHOD.key}" "hello"
            "${SemanticAttributes.NET_PEER_IP.key}" String
            "${SemanticAttributes.NET_PEER_NAME.key}" { it == null || it instanceof String }
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
          }
        }
      }
      trace(1, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "org.apache.dubbo.rpc.service.GenericService/\$invokeAsync"
          kind CLIENT
          childOf span(0)
          errored false
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "dubbo"
            "${SemanticAttributes.RPC_SERVICE.key}" "org.apache.dubbo.rpc.service.GenericService"
            "${SemanticAttributes.RPC_METHOD.key}" "\$invokeAsync"
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
          }
        }
        span(2) {
          name "io.opentelemetry.instrumentation.dubbo.apache.v2_7.api.HelloService/hello"
          kind SERVER
          childOf span(1)
          errored false
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "dubbo"
            "${SemanticAttributes.RPC_SERVICE.key}" "io.opentelemetry.instrumentation.dubbo.apache.v2_7.api.HelloService"
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

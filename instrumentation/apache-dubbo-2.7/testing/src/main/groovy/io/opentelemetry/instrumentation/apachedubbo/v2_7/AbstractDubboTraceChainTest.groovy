/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService
import io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService
import io.opentelemetry.instrumentation.apachedubbo.v2_7.impl.HelloServiceImpl
import io.opentelemetry.instrumentation.apachedubbo.v2_7.impl.MiddleServiceImpl
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.dubbo.common.utils.NetUtils
import org.apache.dubbo.config.ApplicationConfig
import org.apache.dubbo.config.ProtocolConfig
import org.apache.dubbo.config.ReferenceConfig
import org.apache.dubbo.config.RegistryConfig
import org.apache.dubbo.config.ServiceConfig
import org.apache.dubbo.config.bootstrap.DubboBootstrap
import org.apache.dubbo.rpc.service.GenericService
import spock.lang.Unroll

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.SERVER

@Unroll
abstract class AbstractDubboTraceChainTest extends InstrumentationSpecification {

  def setupSpec() {
    NetUtils.LOCAL_ADDRESS = InetAddress.getLoopbackAddress()
  }

  ReferenceConfig<HelloService> configureClient(int port) {
    ReferenceConfig<HelloService> reference = new ReferenceConfig<>()
    reference.setInterface(HelloService)
    reference.setGeneric("true")
    reference.setUrl("dubbo://localhost:" + port + "/?timeout=30000")
    return reference
  }

  ReferenceConfig<HelloService> configureMiddleClient(int port) {
    ReferenceConfig<MiddleService> reference = new ReferenceConfig<>()
    reference.setInterface(MiddleService)
    reference.setGeneric("true")
    reference.setUrl("dubbo://localhost:" + port + "/?timeout=30000")
    return reference
  }

  ServiceConfig configureServer() {
    def registerConfig = new RegistryConfig()
    registerConfig.setAddress("N/A")
    ServiceConfig<HelloServiceImpl> service = new ServiceConfig<>()
    service.setInterface(HelloService)
    service.setRef(new HelloServiceImpl())
    service.setRegistry(registerConfig)
    return service
  }

  ServiceConfig configureMiddleServer(GenericService genericService) {
    def registerConfig = new RegistryConfig()
    registerConfig.setAddress("N/A")
    ServiceConfig<MiddleServiceImpl> service = new ServiceConfig<>()
    service.setInterface(MiddleService)
    service.setRef(new MiddleServiceImpl(genericService))
    service.setRegistry(registerConfig)
    return service
  }

  def "test apache dubbo base #dubbo"() {
    setup:
    def port = PortUtils.findOpenPort()
    def protocolConfig = new ProtocolConfig()
    protocolConfig.setPort(port)

    DubboBootstrap bootstrap = DubboBootstrap.newInstance()
    bootstrap.application(new ApplicationConfig("dubbo-test-provider"))
      .service(configureServer())
      .protocol(protocolConfig)
      .start()

    def middlePort = PortUtils.findOpenPort()
    def middleProtocolConfig = new ProtocolConfig()
    middleProtocolConfig.setPort(middlePort)

    def reference = configureClient(port)
    DubboBootstrap middleBootstrap = DubboBootstrap.newInstance()
    middleBootstrap.application(new ApplicationConfig("dubbo-demo-middle"))
      .reference(reference)
      .service(configureMiddleServer(reference.get()))
      .protocol(middleProtocolConfig)
      .start()


    def consumerProtocolConfig = new ProtocolConfig()
    consumerProtocolConfig.setRegister(false)

    def middleReference = configureMiddleClient(middlePort)
    DubboBootstrap consumerBootstrap = DubboBootstrap.newInstance()
    consumerBootstrap.application(new ApplicationConfig("dubbo-demo-api-consumer"))
      .reference(middleReference)
      .protocol(consumerProtocolConfig)
      .start()

    when:
    GenericService genericService = middleReference.get()
    def o = new Object[1]
    o[0] = "hello"
    def response = runWithSpan("parent") {
      genericService.$invoke("hello", [String.getName()] as String[], o)
    }

    then:
    response == "hello"
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "org.apache.dubbo.rpc.service.GenericService/\$invoke"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.RPC_SYSTEM" "apache_dubbo"
            "$SemanticAttributes.RPC_SERVICE" "org.apache.dubbo.rpc.service.GenericService"
            "$SemanticAttributes.RPC_METHOD" "\$invoke"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" Long
          }
        }
        span(2) {
          name "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService/hello"
          kind SERVER
          childOf span(1)
          attributes {
            "$SemanticAttributes.RPC_SYSTEM" "apache_dubbo"
            "$SemanticAttributes.RPC_SERVICE" "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.MiddleService"
            "$SemanticAttributes.RPC_METHOD" "hello"
            "net.sock.peer.addr" String
            "net.sock.peer.port" Long
            "net.sock.family" { it == "inet6" || it == null }
          }
        }
        span(3) {
          name "org.apache.dubbo.rpc.service.GenericService/\$invoke"
          kind CLIENT
          childOf span(2)
          attributes {
            "$SemanticAttributes.RPC_SYSTEM" "apache_dubbo"
            "$SemanticAttributes.RPC_SERVICE" "org.apache.dubbo.rpc.service.GenericService"
            "$SemanticAttributes.RPC_METHOD" "\$invoke"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" Long
          }
        }
        span(4) {
          name "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService/hello"
          kind SERVER
          childOf span(3)
          attributes {
            "$SemanticAttributes.RPC_SYSTEM" "apache_dubbo"
            "$SemanticAttributes.RPC_SERVICE" "io.opentelemetry.instrumentation.apachedubbo.v2_7.api.HelloService"
            "$SemanticAttributes.RPC_METHOD" "hello"
            "net.sock.peer.addr" String
            "net.sock.peer.port" Long
            "net.sock.family" { it == "inet6" || it == null }
          }
        }
      }
    }

    cleanup:
    bootstrap.destroy()
    consumerBootstrap.destroy()
  }
}

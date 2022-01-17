/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6

import example.GreeterGrpc
import example.Helloworld
import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.stub.StreamObserver
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import spock.lang.Unroll

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.SERVER

@Unroll
abstract class AbstractGrpcStreamingTest extends InstrumentationSpecification {

  abstract ServerBuilder configureServer(ServerBuilder server)

  abstract ManagedChannelBuilder configureClient(ManagedChannelBuilder client)

  def "test conversation #paramName"() {
    setup:
    def msgCount = serverMessageCount
    def serverReceived = new CopyOnWriteArrayList<>()
    def clientReceived = new CopyOnWriteArrayList<>()
    def error = new AtomicReference()

    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      StreamObserver<Helloworld.Response> conversation(StreamObserver<Helloworld.Response> observer) {
        return new StreamObserver<Helloworld.Response>() {
          @Override
          void onNext(Helloworld.Response value) {

            serverReceived << value.message

            (1..msgCount).each {
              observer.onNext(value)
            }
          }

          @Override
          void onError(Throwable t) {
            error.set(t)
            observer.onError(t)
          }

          @Override
          void onCompleted() {
            observer.onCompleted()
          }
        }
      }
    }
    def port = PortUtils.findOpenPort()
    Server server = configureServer(ServerBuilder.forPort(port).addService(greeter)).build().start()
    ManagedChannelBuilder channelBuilder = configureClient(ManagedChannelBuilder.forAddress("localhost", port))

    // Depending on the version of gRPC usePlainText may or may not take an argument.
    try {
      channelBuilder.usePlaintext()
    } catch (MissingMethodException e) {
      channelBuilder.usePlaintext(true)
    }
    ManagedChannel channel = channelBuilder.build()
    GreeterGrpc.GreeterStub client = GreeterGrpc.newStub(channel).withWaitForReady()

    when:
    def observer2 = client.conversation(new StreamObserver<Helloworld.Response>() {
      @Override
      void onNext(Helloworld.Response value) {
        clientReceived << value.message
      }

      @Override
      void onError(Throwable t) {
        error.set(t)
      }

      @Override
      void onCompleted() {
      }
    })

    clientRange.each {
      def message = Helloworld.Response.newBuilder().setMessage("call $it").build()
      observer2.onNext(message)
    }
    observer2.onCompleted()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "example.Greeter/Conversation"
          kind CLIENT
          hasNoParent()
          attributes {
            "$SemanticAttributes.RPC_SYSTEM" "grpc"
            "$SemanticAttributes.RPC_SERVICE" "example.Greeter"
            "$SemanticAttributes.RPC_METHOD" "Conversation"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
            "$SemanticAttributes.RPC_GRPC_STATUS_CODE" Status.OK.code.value()
            "rpc.grpc.authority" client.getChannel().authority()
          }
          (1..(clientMessageCount * serverMessageCount + clientMessageCount)).each {
            def messageId = it
            event(it - 1) {
              eventName "message"
              attributes {
                "message.type" { it == "SENT" || it == "RECEIVED" }
                "message.id" messageId
              }
            }
          }
        }
        span(1) {
          name "example.Greeter/Conversation"
          kind SERVER
          childOf span(0)
          attributes {
            "$SemanticAttributes.RPC_SYSTEM" "grpc"
            "$SemanticAttributes.RPC_SERVICE" "example.Greeter"
            "$SemanticAttributes.RPC_METHOD" "Conversation"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            // net.peer.name resolves to "127.0.0.1" on windows which is same as net.peer.ip so then not captured
            "$SemanticAttributes.NET_PEER_NAME" { it == "localhost" || it == null }
            "$SemanticAttributes.NET_PEER_PORT" Long
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
            "$SemanticAttributes.RPC_GRPC_STATUS_CODE" Status.OK.code.value()
          }
          (1..(clientMessageCount * serverMessageCount + clientMessageCount)).each {
            def messageId = it
            event(it - 1) {
              eventName "message"
              attributes {
                "message.type" { it == "RECEIVED" || it == "SENT" }
                "message.id" messageId
              }
            }
          }
        }
      }
    }
    error.get() == null
    serverReceived == clientRange.collect { "call $it" }
    clientReceived == serverRange.collect { clientRange.collect { "call $it" } }.flatten().sort()

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    paramName | clientMessageCount | serverMessageCount
    "A"       | 1                  | 1
    "B"       | 2                  | 1
    "C"       | 1                  | 2
    "D"       | 2                  | 2
    "E"       | 3                  | 3

    clientRange = 1..clientMessageCount
    serverRange = 1..serverMessageCount
  }
}

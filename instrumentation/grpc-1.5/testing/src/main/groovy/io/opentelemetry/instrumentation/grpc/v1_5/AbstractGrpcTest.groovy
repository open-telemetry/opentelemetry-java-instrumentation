/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import example.GreeterGrpc
import example.Helloworld
import io.grpc.BindableService
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import spock.lang.Unroll

@Unroll
abstract class AbstractGrpcTest extends InstrumentationSpecification {

  abstract ServerBuilder configureServer(ServerBuilder server)

  abstract ManagedChannelBuilder configureClient(ManagedChannelBuilder client)

  def "test request-response #paramName"() {
    setup:
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        final Helloworld.Response reply = Helloworld.Response.newBuilder().setMessage("Hello $req.name").build()
        responseObserver.onNext(reply)
        responseObserver.onCompleted()
      }
    }
    def port = PortUtils.randomOpenPort()
    Server server = configureServer(ServerBuilder.forPort(port).addService(greeter)).build().start()
    ManagedChannelBuilder channelBuilder = configureClient(ManagedChannelBuilder.forAddress("localhost", port))

    // Depending on the version of gRPC usePlainText may or may not take an argument.
    try {
      channelBuilder.usePlaintext()
    } catch (MissingMethodException e) {
      channelBuilder.usePlaintext(true)
    }
    ManagedChannel channel = channelBuilder.build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    def response = runUnderTrace("parent") {
      client.sayHello(Helloworld.Request.newBuilder().setName(paramName).build())
    }

    then:
    response.message == "Hello $paramName"

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "example.Greeter/SayHello"
          kind CLIENT
          childOf span(0)
          errored false
          event(0) {
            eventName "message"
            attributes {
              "message.type" "SENT"
              "message.id" 1
            }
          }
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "grpc"
            "${SemanticAttributes.RPC_SERVICE.key}" "example.Greeter"
            "${SemanticAttributes.RPC_METHOD.key}" "SayHello"
          }
        }
        span(2) {
          name "example.Greeter/SayHello"
          kind SERVER
          childOf span(1)
          errored false
          event(0) {
            eventName "message"
            attributes {
              "message.type" "RECEIVED"
              "message.id" 1
            }
          }
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "grpc"
            "${SemanticAttributes.RPC_SERVICE.key}" "example.Greeter"
            "${SemanticAttributes.RPC_METHOD.key}" "SayHello"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    paramName << ["some name", "some other name"]
  }

  def "test error - #paramName"() {
    setup:
    def error = grpcStatus.asException()
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        responseObserver.onError(error)
      }
    }
    def port = PortUtils.randomOpenPort()
    Server server = configureServer(ServerBuilder.forPort(port).addService(greeter)).build().start()
    ManagedChannelBuilder channelBuilder = configureClient(ManagedChannelBuilder.forAddress("localhost", port))

    // Depending on the version of gRPC usePlainText may or may not take an argument.
    try {
      channelBuilder.usePlaintext()
    } catch (MissingMethodException e) {
      channelBuilder.usePlaintext(true)
    }
    ManagedChannel channel = channelBuilder.build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    client.sayHello(Helloworld.Request.newBuilder().setName(paramName).build())

    then:
    def e = thrown(StatusRuntimeException)
    e.status.code == grpcStatus.code
    e.status.description == grpcStatus.description

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "example.Greeter/SayHello"
          kind CLIENT
          hasNoParent()
          errored true
          status(StatusCode.ERROR)
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "grpc"
            "${SemanticAttributes.RPC_SERVICE.key}" "example.Greeter"
            "${SemanticAttributes.RPC_METHOD.key}" "SayHello"
          }
        }
        span(1) {
          name "example.Greeter/SayHello"
          kind SERVER
          childOf span(0)
          errored true
          status(StatusCode.ERROR)
          event(0) {
            eventName "message"
            attributes {
              "message.type" "RECEIVED"
              "message.id" 1
            }
          }
          if (grpcStatus.cause != null) {
            errorEvent grpcStatus.cause.class, grpcStatus.cause.message, 1
          }
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "grpc"
            "${SemanticAttributes.RPC_SERVICE.key}" "example.Greeter"
            "${SemanticAttributes.RPC_METHOD.key}" "SayHello"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    paramName                     | grpcStatus
    "Runtime - cause"             | Status.UNKNOWN.withCause(new RuntimeException("some error"))
    "Status - cause"              | Status.PERMISSION_DENIED.withCause(new RuntimeException("some error"))
    "StatusRuntime - cause"       | Status.UNIMPLEMENTED.withCause(new RuntimeException("some error"))
    "Runtime - description"       | Status.UNKNOWN.withDescription("some description")
    "Status - description"        | Status.PERMISSION_DENIED.withDescription("some description")
    "StatusRuntime - description" | Status.UNIMPLEMENTED.withDescription("some description")
  }

  def "test error thrown - #paramName"() {
    setup:
    def error = grpcStatus.asRuntimeException()
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        throw error
      }
    }
    def port = PortUtils.randomOpenPort()
    Server server = configureServer(ServerBuilder.forPort(port).addService(greeter)).build().start()
    ManagedChannelBuilder channelBuilder = configureClient(ManagedChannelBuilder.forAddress("localhost", port))

    // Depending on the version of gRPC usePlainText may or may not take an argument.
    try {
      channelBuilder.usePlaintext()
    } catch (MissingMethodException e) {
      channelBuilder.usePlaintext(true)
    }
    ManagedChannel channel = channelBuilder.build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    client.sayHello(Helloworld.Request.newBuilder().setName(paramName).build())

    then:
    def e = thrown(StatusRuntimeException)
    // gRPC doesn't appear to propagate server exceptions that are thrown, not onError.
    e.status.code == Status.UNKNOWN.code
    e.status.description == null

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "example.Greeter/SayHello"
          kind CLIENT
          hasNoParent()
          errored true
          // NB: Exceptions thrown on the server don't appear to be propagated to the client, at
          // least for the version we test against.
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "grpc"
            "${SemanticAttributes.RPC_SERVICE.key}" "example.Greeter"
            "${SemanticAttributes.RPC_METHOD.key}" "SayHello"
          }
        }
        span(1) {
          name "example.Greeter/SayHello"
          kind SERVER
          childOf span(0)
          errored true
          status(StatusCode.ERROR)
          event(0) {
            eventName "message"
            attributes {
              "message.type" "RECEIVED"
              "message.id" 1
            }
          }
          if (grpcStatus.cause != null) {
            errorEvent grpcStatus.cause.class, grpcStatus.cause.message, 1
          }
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "grpc"
            "${SemanticAttributes.RPC_SERVICE.key}" "example.Greeter"
            "${SemanticAttributes.RPC_METHOD.key}" "SayHello"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    paramName                     | grpcStatus
    "Runtime - cause"             | Status.UNKNOWN.withCause(new RuntimeException("some error"))
    "Status - cause"              | Status.PERMISSION_DENIED.withCause(new RuntimeException("some error"))
    "StatusRuntime - cause"       | Status.UNIMPLEMENTED.withCause(new RuntimeException("some error"))
    "Runtime - description"       | Status.UNKNOWN.withDescription("some description")
    "Status - description"        | Status.PERMISSION_DENIED.withDescription("some description")
    "StatusRuntime - description" | Status.UNIMPLEMENTED.withDescription("some description")
  }

  def "test user context preserved"() {
    setup:
    Context.Key<String> key = Context.key("cat")
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        if (key.get() != "meow") {
          responseObserver.onError(new AssertionError((Object) "context not preserved"))
          return
        }
        if (!io.opentelemetry.api.trace.Span.fromContext(io.opentelemetry.context.Context.current()).getSpanContext().isValid()) {
          responseObserver.onError(new AssertionError((Object) "span not attached"))
          return
        }
        final Helloworld.Response reply = Helloworld.Response.newBuilder().setMessage("Hello $req.name").build()
        responseObserver.onNext(reply)
        responseObserver.onCompleted()
      }
    }
    def port = PortUtils.randomOpenPort()
    Server server
    server = configureServer(ServerBuilder.forPort(port)
      .addService(greeter)
      .intercept(new ServerInterceptor() {
        @Override
        <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
          if (!io.opentelemetry.api.trace.Span.fromContext(io.opentelemetry.context.Context.current()).getSpanContext().isValid()) {
            throw new AssertionError((Object) "span not attached in server interceptor")
          }
          def ctx = Context.current().withValue(key, "meow")
          return Contexts.interceptCall(ctx, call, headers, next)
        }
      }))
      .build().start()
    ManagedChannelBuilder channelBuilder
    channelBuilder = configureClient(ManagedChannelBuilder.forAddress("localhost", port))
      .intercept(new ClientInterceptor() {
        @Override
        <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
          if (!io.opentelemetry.api.trace.Span.fromContext(io.opentelemetry.context.Context.current()).getSpanContext().isValid()) {
            throw new AssertionError((Object) "span not attached in client interceptor")
          }
          def ctx = Context.current().withValue(key, "meow")
          def oldCtx = ctx.attach()
          try {
            return next.newCall(method, callOptions)
          } finally {
            ctx.detach(oldCtx)
          }
        }
      })

    // Depending on the version of gRPC usePlainText may or may not take an argument.
    try {
      channelBuilder.usePlaintext()
    } catch (MissingMethodException e) {
      channelBuilder.usePlaintext(true)
    }
    ManagedChannel channel = channelBuilder.build()
    def client = GreeterGrpc.newStub(channel)

    when:
    AtomicReference<Helloworld.Response> response = new AtomicReference<>()
    AtomicReference<Throwable> error = new AtomicReference<>()
    CountDownLatch latch = new CountDownLatch(1)
    runUnderTrace("parent") {
      client.sayHello(
        Helloworld.Request.newBuilder().setName("test").build(),
        new StreamObserver<Helloworld.Response>() {
          @Override
          void onNext(Helloworld.Response r) {
            if (key.get() != "meow") {
              error.set(new AssertionError((Object) "context not preserved"))
              return
            }
            if (!io.opentelemetry.api.trace.Span.fromContext(io.opentelemetry.context.Context.current()).getSpanContext().isValid()) {
              error.set(new AssertionError((Object) "span not attached"))
              return
            }
            response.set(r)
          }

          @Override
          void onError(Throwable throwable) {
            error.set(throwable)
          }

          @Override
          void onCompleted() {
            latch.countDown()
          }
        })
    }

    latch.await(10, TimeUnit.SECONDS)

    then:
    error.get() == null
    response.get().message == "Hello test"

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "example.Greeter/SayHello"
          kind CLIENT
          childOf span(0)
          errored false
          event(0) {
            eventName "message"
            attributes {
              "message.type" "SENT"
              "message.id" 1
            }
          }
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "grpc"
            "${SemanticAttributes.RPC_SERVICE.key}" "example.Greeter"
            "${SemanticAttributes.RPC_METHOD.key}" "SayHello"
          }
        }
        span(2) {
          name "example.Greeter/SayHello"
          kind SERVER
          childOf span(1)
          errored false
          event(0) {
            eventName "message"
            attributes {
              "message.type" "RECEIVED"
              "message.id" 1
            }
          }
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "grpc"
            "${SemanticAttributes.RPC_SERVICE.key}" "example.Greeter"
            "${SemanticAttributes.RPC_METHOD.key}" "SayHello"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()
  }

  // Regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2285
  def "client error thrown"() {
    setup:
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        // Send a response but don't complete so client can fail itself
        responseObserver.onNext(Helloworld.Response.getDefaultInstance())
      }
    }
    def port = PortUtils.randomOpenPort()
    Server server
    server = configureServer(ServerBuilder.forPort(port)
      .addService(greeter))
      .build().start()
    ManagedChannelBuilder channelBuilder
    channelBuilder = configureClient(ManagedChannelBuilder.forAddress("localhost", port))

    // Depending on the version of gRPC usePlainText may or may not take an argument.
    try {
      channelBuilder.usePlaintext()
    } catch (MissingMethodException e) {
      channelBuilder.usePlaintext(true)
    }
    ManagedChannel channel = channelBuilder.build()
    def client = GreeterGrpc.newStub(channel)

    when:
    AtomicReference<Throwable> error = new AtomicReference<>()
    CountDownLatch latch = new CountDownLatch(1)
    client.sayHello(
      Helloworld.Request.newBuilder().setName("test").build(),
      new StreamObserver<Helloworld.Response>() {
        @Override
        void onNext(Helloworld.Response r) {
          throw new IllegalStateException("illegal")
        }

        @Override
        void onError(Throwable throwable) {
          error.set(throwable)
          latch.countDown()
        }

        @Override
        void onCompleted() {
          latch.countDown()
        }
      })

    latch.await(10, TimeUnit.SECONDS)

    then:
    error.get() != null

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()
  }
}

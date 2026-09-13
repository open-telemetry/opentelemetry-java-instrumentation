/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.grpc.v1_6.AbstractGrpcTest.createChannel;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// Regression test for
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/8923
//
// Run twice by the build: once with the default (propagation disabled) and once with
// otel.instrumentation.grpc.propagate-grpc-deadline=true (see testPropagateGrpcDeadline in
// build.gradle.kts), so the same assertion exercises both configurations of the flag.
class GrpcDeadlinePropagationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final boolean PROPAGATE_GRPC_DEADLINE =
      Boolean.getBoolean("otel.instrumentation.grpc.propagate-grpc-deadline");

  private ExecutorService hopExecutor;
  private Server server;
  private ManagedChannel channel;

  @AfterEach
  void tearDown() throws Exception {
    if (channel != null) {
      channel.shutdownNow().awaitTermination(10, SECONDS);
    }
    if (server != null) {
      server.shutdownNow().awaitTermination();
    }
    if (hopExecutor != null) {
      hopExecutor.shutdownNow();
    }
  }

  @Test
  void deadlinePropagationAcrossExecutorHopMatchesConfiguredFlag() throws Exception {
    hopExecutor = Executors.newSingleThreadExecutor();

    CountDownLatch checkedDeadline = new CountDownLatch(1);
    AtomicBoolean deadlineSeenOnHoppedThread = new AtomicBoolean();

    server =
        ServerBuilder.forPort(0)
            .addService(
                new GreeterGrpc.GreeterImplBase() {
                  @Override
                  public void sayHello(
                      Helloworld.Request request,
                      StreamObserver<Helloworld.Response> responseObserver) {
                    // The agent instruments ExecutorService, so this hop only carries the gRPC
                    // deadline if the javaagent's ContextStorageBridge was configured to
                    // propagate it.
                    hopExecutor.execute(
                        () -> {
                          deadlineSeenOnHoppedThread.set(Context.current().getDeadline() != null);
                          checkedDeadline.countDown();
                        });
                    responseObserver.onNext(
                        Helloworld.Response.newBuilder().setMessage(request.getName()).build());
                    responseObserver.onCompleted();
                  }
                })
            .build()
            .start();
    channel = createChannel(ManagedChannelBuilder.forAddress("localhost", server.getPort()));

    GreeterGrpc.GreeterBlockingStub client =
        GreeterGrpc.newBlockingStub(channel).withDeadlineAfter(30, SECONDS);
    client.sayHello(Helloworld.Request.newBuilder().setName("test").build());

    checkedDeadline.await(10, SECONDS);

    assertThat(deadlineSeenOnHoppedThread).hasValue(PROPAGATE_GRPC_DEADLINE);
  }
}

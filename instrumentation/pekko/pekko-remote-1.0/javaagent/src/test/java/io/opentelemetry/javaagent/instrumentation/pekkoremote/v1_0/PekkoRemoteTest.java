/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CountDownLatch;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.actor.Props;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PekkoRemoteTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static ActorSystem senderSystem;
  private static ActorSystem receiverSystem;
  private static String receiverPath;

  @BeforeAll
  static void setUp() {
    senderSystem = ActorSystem.create("sender", remoteConfig());
    receiverSystem = ActorSystem.create("receiver", remoteConfig());
    receiverSystem.actorOf(Props.create(EchoActor.class), "echo");
    receiverPath =
        ((ExtendedActorSystem) receiverSystem).provider().getDefaultAddress() + "/user/echo";
  }

  @AfterAll
  static void tearDown() {
    if (senderSystem != null) {
      senderSystem.terminate();
    }
    if (receiverSystem != null) {
      receiverSystem.terminate();
    }
  }

  private static Config remoteConfig() {
    return ConfigFactory.parseString(
            "pekko.actor.provider = remote\n"
                + "pekko.remote.artery.transport = tcp\n"
                + "pekko.remote.artery.canonical.hostname = 127.0.0.1\n"
                + "pekko.remote.artery.canonical.port = 0\n"
                + "pekko.loglevel = WARNING\n")
        .withFallback(ConfigFactory.load());
  }

  private static void send(String message) throws InterruptedException {
    EchoActor.received = new CountDownLatch(1);
    senderSystem.actorSelection(receiverPath).tell(message, ActorRef.noSender());
    if (!EchoActor.received.await(30, SECONDS)) {
      throw new AssertionError("remote actor did not receive the message");
    }
  }

  @Test
  void propagatesContextToRemoteActor() throws InterruptedException {
    testing.runWithSpan("parent", () -> send("hello"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("remote").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));
  }

  @Test
  void doesNotPropagateContextThatIsNotThere() throws InterruptedException {
    send("hello");

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("remote").hasNoParent()));
  }

  @Test
  void doesNotReuseContextOfPreviousMessage() throws InterruptedException {
    testing.runWithSpan("parent", () -> send("hello"));
    // envelopes are pooled, a message that is sent without a context must not pick up the context
    // of the message that used the envelope before it
    send("hello");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("remote").hasParent(trace.getSpan(0))),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("remote").hasNoParent()));
  }

  public static class EchoActor extends AbstractActor {
    static volatile CountDownLatch received = new CountDownLatch(1);

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              String.class,
              message -> {
                testing.runWithSpan("remote", () -> {});
                received.countDown();
              })
          .build();
    }
  }
}

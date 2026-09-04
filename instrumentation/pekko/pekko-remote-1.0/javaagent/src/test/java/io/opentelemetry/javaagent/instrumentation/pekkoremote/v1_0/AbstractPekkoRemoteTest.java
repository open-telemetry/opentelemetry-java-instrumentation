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
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.actor.Props;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractPekkoRemoteTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  // each subclass tests a different transport and gets an instance of its own, the actor systems
  // and the latch that the receiving actor counts down therefore belong to the instance, a system
  // that is terminated for one transport keeps running for a while and would otherwise be able to
  // count down the latch that the other transport is waiting on
  private ActorSystem senderSystem;
  private ActorSystem receiverSystem;
  private String receiverPath;
  private final AtomicReference<CountDownLatch> received = new AtomicReference<>();

  /** Configuration that sets up the transport under test. */
  protected abstract Config remoteConfig();

  @BeforeAll
  void setUp() {
    senderSystem = ActorSystem.create("sender", remoteConfig());
    receiverSystem = ActorSystem.create("receiver", remoteConfig());
    receiverSystem.actorOf(Props.create(EchoActor.class, received), "echo");
    receiverPath =
        ((ExtendedActorSystem) receiverSystem).provider().getDefaultAddress() + "/user/echo";
  }

  @AfterAll
  void tearDown() {
    if (senderSystem != null) {
      senderSystem.terminate();
    }
    if (receiverSystem != null) {
      receiverSystem.terminate();
    }
  }

  protected static Config parseConfig(String transportConfig) {
    return ConfigFactory.parseString(
            "pekko.actor.provider = remote\n" + "pekko.loglevel = WARNING\n" + transportConfig)
        .withFallback(ConfigFactory.load());
  }

  private void send(String message) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    received.set(latch);
    senderSystem.actorSelection(receiverPath).tell(message, ActorRef.noSender());
    if (!latch.await(30, SECONDS)) {
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
    private final AtomicReference<CountDownLatch> received;

    public EchoActor(AtomicReference<CountDownLatch> received) {
      this.received = received;
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              String.class,
              message -> {
                testing.runWithSpan("remote", () -> {});
                received.get().countDown();
              })
          .build();
    }
  }
}

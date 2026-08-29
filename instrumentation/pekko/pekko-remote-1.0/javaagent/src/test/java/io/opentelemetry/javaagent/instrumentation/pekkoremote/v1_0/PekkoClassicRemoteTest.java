/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.typesafe.config.Config;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.actor.Props;
import org.junit.jupiter.api.Test;

class PekkoClassicRemoteTest extends AbstractPekkoRemoteTest {

  private static final int MAXIMUM_FRAME_SIZE = 32000;

  @Override
  protected Config remoteConfig() {
    return parseConfig(
        "pekko.remote.artery.enabled = off\n"
            + "pekko.remote.classic.enabled-transports = [\"pekko.remote.classic.netty.tcp\"]\n"
            + "pekko.remote.classic.netty.tcp.hostname = 127.0.0.1\n"
            + "pekko.remote.classic.netty.tcp.port = 0\n");
  }

  /**
   * Classic remoting discards a pdu that is larger than the transport allows, so a message that
   * fits without a context must still be delivered when there is a context to write. The size that
   * just fits is measured rather than assumed, because it depends on the envelope around the
   * message as well as on the configured frame size.
   */
  @Test
  void doesNotStopAMessageThatOnlyJustFitsFromBeingDelivered() throws InterruptedException {
    Config config =
        parseConfig(
            "pekko.remote.artery.enabled = off\n"
                + "pekko.remote.classic.enabled-transports = [\"pekko.remote.classic.netty.tcp\"]\n"
                + "pekko.remote.classic.netty.tcp.hostname = 127.0.0.1\n"
                + "pekko.remote.classic.netty.tcp.port = 0\n"
                + "pekko.remote.classic.netty.tcp.maximum-frame-size = "
                + MAXIMUM_FRAME_SIZE
                + "b\n");
    ActorSystem sender = ActorSystem.create("size-sender", config);
    ActorSystem receiver = ActorSystem.create("size-receiver", config);
    try {
      AtomicReference<CountDownLatch> received = new AtomicReference<>();
      receiver.actorOf(Props.create(CountingActor.class, received), "counter");
      String path =
          ((ExtendedActorSystem) receiver).provider().getDefaultAddress() + "/user/counter";

      // the largest message that is delivered with no context to write, which is the limit the
      // agent must not lower
      int low = 0;
      int high = MAXIMUM_FRAME_SIZE;
      while (low < high) {
        int size = (low + high + 1) / 2;
        if (delivers(sender, path, received, size, 2000)) {
          low = size;
        } else {
          high = size - 1;
        }
      }
      assertThat(low).isGreaterThan(0);

      // the same size, this time with a context that the codec would want to append
      int justFits = low;
      AtomicReference<Boolean> delivered = new AtomicReference<>();
      testing.runWithSpan(
          "parent", () -> delivered.set(delivers(sender, path, received, justFits, 30_000)));
      assertThat(delivered.get()).isTrue();
    } finally {
      sender.terminate();
      receiver.terminate();
    }
  }

  private static boolean delivers(
      ActorSystem sender,
      String path,
      AtomicReference<CountDownLatch> received,
      int size,
      long timeoutMillis) {
    CountDownLatch latch = new CountDownLatch(1);
    received.set(latch);
    StringBuilder message = new StringBuilder(size);
    for (int i = 0; i < size; i++) {
      message.append('x');
    }
    sender.actorSelection(path).tell(message.toString(), ActorRef.noSender());
    try {
      return latch.await(timeoutMillis, MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  /** Counts messages without recording a span, this test is about delivery rather than tracing. */
  public static class CountingActor extends AbstractActor {
    private final AtomicReference<CountDownLatch> received;

    public CountingActor(AtomicReference<CountDownLatch> received) {
      this.received = received;
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder().match(String.class, message -> received.get().countDown()).build();
    }
  }
}

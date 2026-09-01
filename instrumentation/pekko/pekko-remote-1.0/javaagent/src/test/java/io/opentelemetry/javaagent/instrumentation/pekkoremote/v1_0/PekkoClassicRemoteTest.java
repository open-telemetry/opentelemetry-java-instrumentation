/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.typesafe.config.Config;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.actor.Props;
import org.junit.jupiter.api.Test;

class PekkoClassicRemoteTest extends AbstractPekkoRemoteTest {

  private static final int MAXIMUM_FRAME_SIZE = 32000;

  // latches keyed by the probe id the message carries, so that a message which arrives after its
  // probe already timed out counts against its own probe and not against whichever probe runs next
  private final ConcurrentHashMap<String, CountDownLatch> received = new ConcurrentHashMap<>();
  private final AtomicInteger probeIds = new AtomicInteger();

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
  void doesNotStopAMessageThatOnlyJustFitsFromBeingDelivered() {
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
      receiver.actorOf(Props.create(CountingActor.class, received), "counter");
      String path =
          ((ExtendedActorSystem) receiver).provider().getDefaultAddress() + "/user/counter";

      // the largest message that is delivered with no context to write, which is the limit the
      // agent must not lower
      int low = 0;
      int high = MAXIMUM_FRAME_SIZE;
      while (low < high) {
        int size = (low + high + 1) / 2;
        if (delivers(sender, path, size, 2000)) {
          low = size;
        } else {
          high = size - 1;
        }
      }
      assertThat(low).isGreaterThan(0);

      // the same size, this time with a context that the codec would want to append
      int justFits = low;
      AtomicReference<Boolean> delivered = new AtomicReference<>();
      testing.runWithSpan("parent", () -> delivered.set(delivers(sender, path, justFits, 30_000)));
      assertThat(delivered.get()).isTrue();
    } finally {
      sender.terminate();
      receiver.terminate();
    }
  }

  /**
   * Sends a message of the given size and reports whether it arrived within the timeout. The
   * message starts with an id that ties it to its own latch. The id takes up part of the requested
   * size rather than adding to it, which keeps the size honest where it matters: the sizes probed
   * near the frame limit are far larger than the id.
   */
  private boolean delivers(ActorSystem sender, String path, int size, long timeoutMillis) {
    String id = "probe-" + probeIds.incrementAndGet() + ":";
    CountDownLatch latch = new CountDownLatch(1);
    received.put(id, latch);
    StringBuilder message = new StringBuilder(Math.max(size, id.length())).append(id);
    while (message.length() < size) {
      message.append('x');
    }
    sender.actorSelection(path).tell(message.toString(), ActorRef.noSender());
    try {
      return latch.await(timeoutMillis, MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    } finally {
      received.remove(id);
    }
  }

  /** Counts messages without recording a span, this test is about delivery rather than tracing. */
  public static class CountingActor extends AbstractActor {
    private final ConcurrentHashMap<String, CountDownLatch> received;

    public CountingActor(ConcurrentHashMap<String, CountDownLatch> received) {
      this.received = received;
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder().match(String.class, this::countDown).build();
    }

    private void countDown(String message) {
      int idEnd = message.indexOf(':');
      if (idEnd < 0) {
        return;
      }
      CountDownLatch latch = received.get(message.substring(0, idEnd + 1));
      if (latch != null) {
        latch.countDown();
      }
    }
  }
}

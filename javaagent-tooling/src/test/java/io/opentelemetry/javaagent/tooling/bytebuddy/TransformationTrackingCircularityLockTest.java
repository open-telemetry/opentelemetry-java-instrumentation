/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.internal.InTransformation;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class TransformationTrackingCircularityLockTest {

  @Test
  void shouldTrackTransformationWhileTheLockIsHeld() {
    TransformationTrackingCircularityLock underTest = new TransformationTrackingCircularityLock();

    assertThat(InTransformation.get()).isFalse();

    assertThat(underTest.acquire()).isTrue();
    assertThat(InTransformation.get()).isTrue();

    underTest.release();
    assertThat(InTransformation.get()).isFalse();
  }

  @Test
  void shouldStayTrackedWhenTheLockIsAlreadyHeldByThisThread() {
    TransformationTrackingCircularityLock underTest = new TransformationTrackingCircularityLock();

    assertThat(underTest.acquire()).isTrue();
    // byte-buddy returns without transforming when the lock can't be acquired, and does not
    // release it - the thread is still inside the outer transformation
    assertThat(underTest.acquire()).isFalse();
    assertThat(InTransformation.get()).isTrue();

    underTest.release();
    assertThat(InTransformation.get()).isFalse();
  }

  @Test
  void shouldStayTrackedUntilTheOutermostTransformationCompletes() {
    // more than one transformer can be installed, each with its own lock, and a class loaded
    // during a transformation is handed to all of them
    TransformationTrackingCircularityLock outer = new TransformationTrackingCircularityLock();
    TransformationTrackingCircularityLock nested = new TransformationTrackingCircularityLock();

    assertThat(outer.acquire()).isTrue();
    assertThat(nested.acquire()).isTrue();

    nested.release();
    assertThat(InTransformation.get()).isTrue();

    outer.release();
    assertThat(InTransformation.get()).isFalse();
  }

  @Test
  void shouldNotTrackOtherThreads() throws Exception {
    TransformationTrackingCircularityLock underTest = new TransformationTrackingCircularityLock();
    AtomicBoolean otherThreadSawTransformation = new AtomicBoolean(true);
    CountDownLatch done = new CountDownLatch(1);

    assertThat(underTest.acquire()).isTrue();
    try {
      Thread other =
          new Thread(
              () -> {
                otherThreadSawTransformation.set(InTransformation.get());
                done.countDown();
              });
      other.start();
      assertThat(done.await(10, SECONDS)).isTrue();
    } finally {
      underTest.release();
    }

    assertThat(otherThreadSawTransformation).isFalse();
  }
}

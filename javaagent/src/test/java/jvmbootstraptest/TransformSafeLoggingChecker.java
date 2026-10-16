/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.javaagent.IntegrationTestUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class TransformSafeLoggingChecker {

  public static void main(String[] args) throws Exception {
    boolean enabled = args[0].equals("true");
    ClassLoader agentLoader = IntegrationTestUtils.getAgentClassLoader();
    Class<?> lockClass =
        agentLoader.loadClass("io.opentelemetry.javaagent.tooling.internal.AgentCircularityLock");
    Object lock = lockClass.getField("INSTANCE").get(null);
    Method acquire = lockClass.getMethod("acquire");
    Method release = lockClass.getMethod("release");
    Method isHeld = lockClass.getMethod("isHeldByCurrentThread");

    // Invoke an ordinary JUL caller from the built, shaded agent, not the removed logging wrapper.
    Class<?> listenerClass =
        agentLoader.loadClass(
            "io.opentelemetry.javaagent.tooling.AgentInstaller$TransformLoggingListener");
    Constructor<?> constructor = listenerClass.getDeclaredConstructor();
    constructor.setAccessible(true);
    Object listener = constructor.newInstance();
    Method onError = null;
    for (Method method : listenerClass.getDeclaredMethods()) {
      if (method.getName().equals("onError")) {
        onError = method;
      }
    }
    assertThat(onError).isNotNull();
    onError.setAccessible(true);
    if (enabled) {
      awaitLoggingQueueDrained();
    }

    PrintStream original = System.err;
    RecordingOutput output = new RecordingOutput(enabled);
    System.setErr(output);
    try {
      assertThat(isHeld.invoke(lock)).isEqualTo(false);
      onError.invoke(listener, "routing-before", null, null, false, new Exception("before"));
      assertThat(output.threads.poll(10, SECONDS)).isSameAs(Thread.currentThread());

      assertThat(acquire.invoke(lock)).isEqualTo(true);
      try {
        assertThat(isHeld.invoke(lock)).isEqualTo(true);
        onError.invoke(listener, "routing-locked", null, null, false, new Exception("locked"));
        assertThat(output.entered.await(10, SECONDS)).isTrue();
      } finally {
        release.invoke(lock);
      }
      Thread loggingThread = output.threads.poll(10, SECONDS);
      if (enabled) {
        assertThat(loggingThread).isNotSameAs(Thread.currentThread());
        assertThat(loggingThread.getName()).isEqualTo("otel-javaagent-transform-safe-logger");
        assertThat(loggingThread.getContextClassLoader()).isNull();
      } else {
        assertThat(loggingThread).isSameAs(Thread.currentThread());
      }

      // A record outside the lock must not overtake one still being delivered by the worker.
      onError.invoke(listener, "routing-after", null, null, false, new Exception("after"));
      if (enabled) {
        assertThat(output.threads).isEmpty();
      }
      output.unblock.countDown();
      assertThat(output.threads.poll(10, SECONDS)).isSameAs(loggingThread);

      if (enabled) {
        awaitLoggingQueueDrained();
      }
      onError.invoke(listener, "routing-drained", null, null, false, new Exception("drained"));
      assertThat(output.threads.poll(10, SECONDS)).isSameAs(Thread.currentThread());
    } finally {
      output.unblock.countDown();
      System.setErr(original);
      output.close();
    }
  }

  private static void awaitLoggingQueueDrained() throws Exception {
    Class<?> loggingClass =
        Class.forName("io.opentelemetry.javaagent.bootstrap.TransformSafeLogging");
    Field dispatcherField = loggingClass.getDeclaredField("dispatcher");
    dispatcherField.setAccessible(true);
    Object dispatcher = dispatcherField.get(null);
    Field pendingField = dispatcher.getClass().getDeclaredField("pending");
    pendingField.setAccessible(true);
    AtomicInteger pending = (AtomicInteger) pendingField.get(dispatcher);
    await().until(() -> pending.get() == 0);
  }

  private static final class RecordingOutput extends PrintStream {
    final BlockingQueue<Thread> threads = new LinkedBlockingQueue<>();
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch unblock = new CountDownLatch(1);
    private final boolean enabled;

    RecordingOutput(boolean enabled) {
      super(new ByteArrayOutputStream());
      this.enabled = enabled;
    }

    @Override
    public void println(String message) {
      if (!message.contains("Failed to handle routing-")) {
        return;
      }
      threads.add(Thread.currentThread());
      if (message.contains("routing-locked")) {
        entered.countDown();
        if (enabled) {
          try {
            assertThat(unblock.await(10, SECONDS)).isTrue();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
        }
      }
    }
  }

  private TransformSafeLoggingChecker() {}
}

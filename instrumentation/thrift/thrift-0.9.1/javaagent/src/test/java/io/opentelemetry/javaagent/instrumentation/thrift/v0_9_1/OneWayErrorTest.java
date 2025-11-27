/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift.ThriftService;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class OneWayErrorTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncClientSyncSimpleServerOneWayWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientOneWayWithError(port);
    }
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 5);
  }

  @Test
  public void syncClientSyncSimpleServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientOneWayWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayWithError()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientOneWayWithError(port);
    }
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 5);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientOneWayWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    this.syncClientMultiOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        port, "oneWayWithError", "syncHelloWorld:oneWayWithError", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWayWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiOneWayWithError(port);
    }
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        port, "oneWayWithError", "syncHelloWorld:oneWayWithError", 5);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientMultiOneWayWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        port, "oneWayWithError", "syncHelloWorld:oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWayWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    this.syncFramedClientMultiOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        port, "oneWayWithError", "syncHelloWorld:oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWayWithErrorMuti()
      throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiOneWayWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        port, "oneWayWithError", "syncHelloWorld:oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientMultiOneWayWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        port, "oneWayWithError", "syncHelloWorld:oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWayWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncThreadedSelectorServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWayWithError()
      throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWayWithErrorMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncThreadedSelectorServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWayWithError()
      throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWayWithErrorMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncThreadedSelectorServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncNonblockingServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncHsHaServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWayWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncNonblockingServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWayWithError() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.syncFramedClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWayWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncHsHaServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayWithError() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayWithErrorMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncNonblockingServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayWithError() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayWithErrorMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncHsHaServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWayWithError() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWayWithErrorMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncNonblockingServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWayWithError() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.nonBlockClientOneWayWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWayWithErrorMuti() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncHsHaServerOneWayWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncError(port, "oneWayWithError", threadCount);
  }

  public void syncClientOneWayWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.oneWayWithError());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncClientMultiOneWayWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      this.testing().runWithSpan("parent", () -> client.oneWayWithError());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void nonBlockClientOneWayWithError(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<ThriftService.AsyncClient.oneWayWithError_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.oneWayWithError_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.oneWayWithError_call no) {}

          @Override
          public void onError(Exception e) {
            assertThat(e.getMessage()).isEqualTo("Read call frame size failed");
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.oneWayWithError(callback));
  }

  public void syncFramedClientOneWayWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.oneWayWithError());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncFramedClientMultiOneWayWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      this.testing().runWithSpan("parent", () -> client.oneWayWithError());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }
}

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
import org.testcontainers.shaded.com.google.common.base.VerifyException;

public class WithErrorTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerWithError() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void syncClientSyncSimpleServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithError(port);
    }
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 5);
  }

  @Test
  public void syncClientSyncSimpleServerWithErrorParallel()
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
                  this.syncClientWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithError() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithError(port);
    }
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 5);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithErrorParallel()
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
                  this.syncClientWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithError() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    this.syncClientMultiWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        port, "withError", "syncHelloWorld:withError", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiWithError(port);
    }
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        port, "withError", "syncHelloWorld:withError", 5);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithErrorParallel()
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
                  this.syncClientMultiWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        port, "withError", "syncHelloWorld:withError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithError() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", 5);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithErrorParallel()
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
                  this.syncFramedClientWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithError() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    this.syncFramedClientMultiWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        port, "withError", "syncHelloWorld:withError", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncWithError(
        port, "withError", "syncHelloWorld:withError", 5);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithErrorParallel()
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
                  this.syncFramedClientMultiWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        port, "withError", "syncHelloWorld:withError", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithError() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 5);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithErrorParallel()
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
                  this.syncFramedClientWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncThreadedSelectorServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithErrorMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", 5);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithErrorParallel()
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
                  this.nonBlockClientWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncThreadedSelectorServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithErrorMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", 5);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithErrorParallel()
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
                  this.nonBlockClientWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncThreadedSelectorServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithError() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithErrorParallel()
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
                  this.syncFramedClientWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncNonblockingServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithError() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithErrorParallel()
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
                  this.syncFramedClientWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncHsHaServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithError() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", 5);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithErrorParallel()
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
                  this.syncFramedClientWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncNonblockingServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithError() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", 5);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithErrorParallel()
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
                  this.syncFramedClientWithError(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncHsHaServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithErrorMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithErrorParallel()
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
                  this.nonBlockClientWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncNonblockingServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithErrorMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithErrorParallel()
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
                  this.nonBlockClientWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncHsHaServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithErrorMuti() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", 5);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithErrorParallel()
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
                  this.nonBlockClientWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncNonblockingServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithErrorMuti() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", 5);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithErrorParallel()
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
                  this.nonBlockClientWithError(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncHsHaServerWithErrorParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", threadCount);
  }

  public void syncClientWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      Exception error = null;
      try {
        this.testing().runWithSpan("parent", () -> client.withError());
      } catch (Exception e) {
        error = e;
      }
      assertThat(error).isNotNull();
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncClientMultiWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      Exception error = null;
      try {
        this.testing().runWithSpan("parent", () -> client.withError());
      } catch (Exception e) {
        error = e;
      }
      assertThat(error).isNotNull();
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void nonBlockClientWithError(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<ThriftService.AsyncClient.withError_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.withError_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.withError_call s) {
            try {
              String result = s.getResult();
              assertThat(result).isEqualTo("Hello USs' Bob");
            } catch (TException e) {
              throw new VerifyException(e);
            }
          }

          @Override
          public void onError(Exception e) {
            assertThat(e.getMessage()).isEqualTo("Read call frame size failed");
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.withError(callback));
  }

  public void syncFramedClientWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      Exception error = null;
      try {
        this.testing().runWithSpan("parent", () -> client.withError());
      } catch (Exception e) {
        error = e;
      }
      assertThat(error).isNotNull();
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncFramedClientMultiWithError(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      Exception error = null;
      try {
        this.testing().runWithSpan("parent", () -> client.withError());
      } catch (Exception e) {
        error = e;
      }
      assertThat(error).isNotNull();
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }
}

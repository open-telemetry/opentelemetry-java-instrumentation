/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_9_2;

import io.opentelemetry.instrumentation.thrift.v0_9_2.thrift.ThriftService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.sasl.Sasl;
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
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class OneWayErrorTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerOneWayWithError() throws TException {
    this.startSyncSimpleServer(this.port);
    this.syncClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", 1);
  }

  @Test
  public void syncClientSyncSimpleServerOneWayWithErrorMuti() throws TException {
    this.startSyncSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientOneWayWithError(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", 5);
  }

  @Test
  public void syncClientSyncSimpleServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayWithError()
      throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientOneWayWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientOneWayWithError(port);
    }
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", 5);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWayWithError() throws TException {
    this.startMultiSimpleServer(this.port);
    this.syncClientMultiOneWayWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        "oneWayWithError", "syncHelloWorld:oneWayWithError", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWayWithErrorMuti() throws TException {
    this.startMultiSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiOneWayWithError(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        "oneWayWithError", "syncHelloWorld:oneWayWithError", 5);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startMultiSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientMultiOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        "oneWayWithError", "syncHelloWorld:oneWayWithError", threadCount);
  }

  @Test
  public void syncClientSyncThreadedSelectorServerOneWayWithErrorError() {
    Exception error = null;
    try {
      this.startSyncThreadedSelectorServer(this.port);
      this.syncClientOneWayWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWayWithError", 1);
  }

  @Test
  public void syncClientAsyncThreadedSelectorServerOneWayWithErrorError() {
    Exception error = null;
    try {
      this.startAsyncThreadedSelectorServer(this.port);
      this.syncClientOneWayWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWayWithError() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.syncFramedClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientSyncServerAsyncError("oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWayWithErrorMuti() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncError("oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncError("oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWayWithError() throws TException {
    this.startMultiThreadedSelectorServer(this.port);
    this.syncFramedClientMultiOneWayWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        "oneWayWithError", "syncHelloWorld:oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWayWithErrorMuti()
      throws TException {
    this.startMultiThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        "oneWayWithError", "syncHelloWorld:oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startMultiThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientMultiOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError(
        "oneWayWithError", "syncHelloWorld:oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWayWithError() throws TException {
    this.startSyncThreadedSelectorServer(this.port);
    this.syncFramedClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWayWithErrorMuti() throws TException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWayWithError()
      throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.nonBlockClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerAsyncError("oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWayWithErrorMuti()
      throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncError("oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncError("oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWayWithError()
      throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);
    this.nonBlockClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError("oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWayWithErrorMuti()
      throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncOnewayError("oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError("oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayWithError() throws TException {
    this.startSyncNonblockingServer(this.port);
    this.syncFramedClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayWithErrorMuti() throws TException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayWithError() throws TException {
    this.startSyncHsHaServer(this.port);
    this.syncFramedClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayWithErrorMuti() throws TException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncOnewayError("oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWayWithError() throws TException {
    this.startAsyncNonblockingServer(this.port);
    this.syncFramedClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientSyncServerAsyncError("oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWayWithErrorMuti() throws TException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncError("oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncError("oneWayWithError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWayWithError() throws TException {
    this.startAsyncHsHaServer(this.port);
    this.syncFramedClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientSyncServerAsyncError("oneWayWithError", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWayWithErrorMuti() throws TException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncError("oneWayWithError", 5);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncError("oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayWithError() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);
    this.nonBlockClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError("oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayWithErrorMuti()
      throws TException, IOException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncOnewayError("oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError("oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayWithError() throws TException, IOException {
    this.startSyncHsHaServer(this.port);
    this.nonBlockClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError("oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayWithErrorMuti() throws TException, IOException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncOnewayError("oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncOnewayError("oneWayWithError", threadCount);
  }

  @Test
  public void syncClientAsyncNonblockingServerOneWayWithErrorError() {
    Exception error = null;
    try {
      this.startAsyncNonblockingServer(this.port);
      this.syncClientOneWayWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWayWithError", 1);
  }

  @Test
  public void syncClientSyncNonblockingServerOneWayWithErrorError() {
    Exception error = null;
    try {
      this.startSyncNonblockingServer(this.port);
      this.syncClientOneWayWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWayWithError", 1);
  }

  @Test
  public void syncClientAsyncHsHaServerOneWayWithErrorError() {
    Exception error = null;
    try {
      this.startAsyncHsHaServer(this.port);
      this.syncClientOneWayWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWayWithError", 1);
  }

  @Test
  public void syncClientSyncHsHaServerOneWayWithErrorError() {
    Exception error = null;
    try {
      this.startSyncHsHaServer(this.port);
      this.syncClientOneWayWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWayWithError() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);
    this.nonBlockClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerAsyncError("oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWayWithErrorMuti()
      throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncError("oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncError("oneWayWithError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWayWithError() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);
    this.nonBlockClientOneWayWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerAsyncError("oneWayWithError", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWayWithErrorMuti() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWayWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncError("oneWayWithError", 5);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWayWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWayWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncError("oneWayWithError", threadCount);
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
            Assertions.assertThat(e.getCause().getMessage())
                .isEqualTo("Read call frame size failed");
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

  public void syncNonblockingSaslClientOneWayWithError(int port) throws IOException {
    Map<String, String> saslOptions = new HashMap<String, String>();
    saslOptions.put(Sasl.QOP, "auth");
    saslOptions.put(Sasl.SERVER_AUTH, "true");
    TNonblockingTransport nonblockingTransport = new TNonblockingSocket("localhost", port);
    TTransport transport =
        new TSaslClientTransport(
            "PLAIN", // SASL 机制
            null, // Authorization ID
            null, // Authentication ID (用户名)
            null, // Password
            saslOptions, // SASL properties
            new TestSaslCallbackHandler("12345"), // Callback handler
            nonblockingTransport // 底层传输
            );
    TProtocol protocol = new TBinaryProtocol(transport);
    ThriftService.Client client = new ThriftService.Client(protocol);
    Exception error = null;
    try {
      this.testing().runWithSpan("parent", () -> client.oneWayWithError());
    } catch (Exception e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
  }
}

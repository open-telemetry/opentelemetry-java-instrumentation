/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_9_3;

import io.opentelemetry.instrumentation.thrift.v0_9_3.common.TMultiplexedProtocolFactory;
import io.opentelemetry.instrumentation.thrift.v0_9_3.thrift.ThriftService;
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

public class OneWayTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerOneWay() throws TException {
    this.startSyncSimpleServer(this.port);
    this.syncClientOneWay(this.port);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", 1);
  }

  @Test
  public void syncClientSyncSimpleServerOneWayMuti() throws TException {
    this.startSyncSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientOneWay(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSync("oneWay", 5);
  }

  @Test
  public void syncClientSyncSimpleServerOneWayParallel() throws TException, InterruptedException {
    this.startSyncSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientOneWay(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerOneWayParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWay() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientOneWay(port);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientOneWay(port);
    }
    this.waitAndAssertTracesClientSyncServerSync("oneWay", 5);
  }

  @Test
  public void syncClientSyncThreadPoolServerOneWayParallel()
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
                  this.syncClientOneWay(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerOneWayParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWay() throws TException {
    this.startMultiSimpleServer(this.port);
    this.syncClientMultiOneWay(this.port);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", "syncHelloWorld:oneWay", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWayMuti() throws TException {
    this.startMultiSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiOneWay(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSync("oneWay", "syncHelloWorld:oneWay", 5);
  }

  @Test
  public void syncClientMutiSyncSimpleServerOneWayParallel()
      throws TException, InterruptedException {
    this.startMultiSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientMultiOneWay(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerOneWayParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", "syncHelloWorld:oneWay", threadCount);
  }

  @Test
  public void syncClientSyncThreadedSelectorServerOneWayError() {
    Exception error = null;
    try {
      this.startSyncThreadedSelectorServer(this.port);
      this.syncClientOneWay(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWay", 1);
  }

  @Test
  public void syncClientAsyncThreadedSelectorServerOneWayError() {
    Exception error = null;
    try {
      this.startAsyncThreadedSelectorServer(this.port);
      this.syncClientOneWay(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWay", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWay() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.syncFramedClientOneWay(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("oneWay", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWayMuti() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("oneWay", 5);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerOneWayParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWay(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("oneWay", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWay() throws TException {
    this.startMultiThreadedSelectorServer(this.port);
    this.syncFramedClientMultiOneWay(this.port);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", "syncHelloWorld:oneWay", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWayMuti() throws TException {
    this.startMultiThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiOneWay(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("oneWay", "syncHelloWorld:oneWay", 5);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerOneWayParallel()
      throws TException, InterruptedException {
    this.startMultiThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientMultiOneWay(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", "syncHelloWorld:oneWay", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWay() throws TException {
    this.startSyncThreadedSelectorServer(this.port);
    this.syncFramedClientOneWay(this.port);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWayMuti() throws TException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("oneWay", 5);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerOneWayParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWay(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncThreadedSelectorServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWay() throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.nonBlockClientOneWay(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("oneWay", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWayMuti() throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("oneWay", 5);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerOneWayParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWay(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncThreadedSelectorServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("oneWay", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWay() throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);
    this.nonBlockClientOneWay(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("oneWay", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWayMuti() throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("oneWay", 5);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerOneWayParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWay(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncThreadedSelectorServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("oneWay", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWay() throws TException {
    this.startSyncNonblockingServer(this.port);
    this.syncFramedClientOneWay(this.port);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayMuti() throws TException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("oneWay", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerOneWayParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWay(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncNonblockingServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWay() throws TException {
    this.startSyncHsHaServer(this.port);
    this.syncFramedClientOneWay(this.port);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayMuti() throws TException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("oneWay", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerOneWayParallel()
      throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWay(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncHsHaServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("oneWay", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWay() throws TException {
    this.startAsyncNonblockingServer(this.port);
    this.syncFramedClientOneWay(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("oneWay", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWayMuti() throws TException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("oneWay", 5);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerOneWayParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWay(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncNonblockingServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("oneWay", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWay() throws TException {
    this.startAsyncHsHaServer(this.port);
    this.syncFramedClientOneWay(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("oneWay", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWayMuti() throws TException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("oneWay", 5);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerOneWayParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientOneWay(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncHsHaServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("oneWay", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWay() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);
    this.nonBlockClientOneWay(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("oneWay", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayMuti() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("oneWay", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerOneWayParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWay(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncNonblockingServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("oneWay", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWay() throws TException, IOException {
    this.startSyncHsHaServer(this.port);
    this.nonBlockClientOneWay(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("oneWay", 1);
  }

  @Test
  public void nonBlockMultiClientSyncHsHaServerMultiOneWay() throws TException, IOException {
    this.startMultiSyncHsHaServer(this.port);
    this.nonBlockClientMultiOneWay(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("oneWay", "syncHelloWorld:oneWay", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayMuti() throws TException, IOException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("oneWay", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerOneWayParallel() throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWay(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncHsHaServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("oneWay", threadCount);
  }

  @Test
  public void syncClientAsyncNonblockingServerOneWayError() {
    Exception error = null;
    try {
      this.startAsyncNonblockingServer(this.port);
      this.syncClientOneWay(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWay", 1);
  }

  @Test
  public void syncClientSyncNonblockingServerOneWayError() {
    Exception error = null;
    try {
      this.startSyncNonblockingServer(this.port);
      this.syncClientOneWay(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWay", 1);
  }

  @Test
  public void syncClientAsyncHsHaServerOneWayError() {
    Exception error = null;
    try {
      this.startAsyncHsHaServer(this.port);
      this.syncClientOneWay(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWay", 1);
  }

  @Test
  public void syncClientSyncHsHaServerOneWayError() {
    Exception error = null;
    try {
      this.startSyncHsHaServer(this.port);
      this.syncClientOneWay(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerOneway("oneWay", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWay() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);
    this.nonBlockClientOneWay(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("oneWay", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWayMuti() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("oneWay", 5);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerOneWayParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWay(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncNonblockingServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("oneWay", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWay() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);
    this.nonBlockClientOneWay(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("oneWay", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWayMuti() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientOneWay(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("oneWay", 5);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerOneWayParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientOneWay(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncHsHaServerOneWayParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("oneWay", threadCount);
  }

  public void syncClientOneWay(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.oneWay());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncClientMultiOneWay(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      this.testing().runWithSpan("parent", () -> client.oneWay());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void nonBlockClientOneWay(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<ThriftService.AsyncClient.oneWay_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.oneWay_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.oneWay_call no) {}

          @Override
          public void onError(Exception e) {
            Assertions.assertThat(e.getCause().getMessage())
                .isEqualTo("Read call frame size failed");
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.oneWay(callback));
  }

  public void nonBlockClientMultiOneWay(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    TMultiplexedProtocolFactory multiplexedProtocolFactory =
        new TMultiplexedProtocolFactory(protocolFactory, "syncHelloWorld");
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, multiplexedProtocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<ThriftService.AsyncClient.oneWay_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.oneWay_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.oneWay_call no) {}

          @Override
          public void onError(Exception e) {
            Assertions.assertThat(e.getCause().getMessage())
                .isEqualTo("Read call frame size failed");
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.oneWay(callback));
  }

  public void syncFramedClientOneWay(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.oneWay());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncFramedClientMultiOneWay(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      this.testing().runWithSpan("parent", () -> client.oneWay());
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncNonblockingSaslClientOneWay(int port) throws IOException {
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
      this.testing().runWithSpan("parent", () -> client.oneWay());
    } catch (Exception e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
  }
}

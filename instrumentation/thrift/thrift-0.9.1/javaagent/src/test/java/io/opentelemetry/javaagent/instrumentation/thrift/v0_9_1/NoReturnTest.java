/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1;

import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift.ThriftService;
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
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class NoReturnTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncClientSyncSimpleServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientNoReturn(port);
    }
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 5);
  }

  @Test
  public void syncClientSyncSimpleServerNoReturnParallel() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerNoReturnParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerNoReturn() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientNoReturn(port);
    }
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 5);
  }

  @Test
  public void syncClientSyncThreadPoolServerNoReturnParallel()
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
                  this.syncClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerNoReturnParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerNoReturn() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    this.syncClientMultiNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", "syncHelloWorld:noReturn", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiNoReturn(port);
    }
    this.waitAndAssertTracesClientSyncServerSync("noReturn", "syncHelloWorld:noReturn", 5);
  }

  @Test
  public void syncClientMutiSyncSimpleServerNoReturnParallel()
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
                  this.syncClientMultiNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerNoReturnParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(
        "noReturn", "syncHelloWorld:noReturn", threadCount);
  }

  @Test
  public void syncClientSyncThreadedSelectorServerNoReturnError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startSyncThreadedSelectorServer(port);
      this.syncClientNoReturn(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("noReturn", 1);
  }

  @Test
  public void syncClientAsyncThreadedSelectorServerNoReturnError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startAsyncThreadedSelectorServer(port);
      this.syncClientNoReturn(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("noReturn", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 1);
  }

  @Test
  public void syncFastFramedClientAsyncThreadedSelectorServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorFastServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 1);
  }

  @Test
  public void syncFastFramedClientAsyncThreadedSelectorFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorFastServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientNoReturn(port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 5);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerNoReturnParallel()
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
                  this.syncFramedClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerNoReturn() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    this.syncFramedClientMultiNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", "syncHelloWorld:noReturn", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiNoReturn(port);
    }
    this.waitAndAssertTracesClientSyncServerSync("noReturn", "syncHelloWorld:noReturn", 5);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerNoReturnParallel()
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
                  this.syncFramedClientMultiNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(
        "noReturn", "syncHelloWorld:noReturn", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFastFramedClientSyncThreadedSelectorServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorFastServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFastFramedClientSyncThreadedSelectorFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorFastServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientNoReturn(port);
    }
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 5);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerNoReturnParallel()
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
                  this.syncFramedClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncThreadedSelectorServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerAsync("noReturn", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerNoReturnMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientNoReturn(port);
    }
    this.waitAndAssertTracesClientAsyncServerAsync("noReturn", 5);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerNoReturnParallel()
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
                  this.nonBlockClientNoReturn(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncThreadedSelectorServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("noReturn", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerSync("noReturn", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerNoReturnMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientNoReturn(port);
    }
    this.waitAndAssertTracesClientAsyncServerSync("noReturn", 5);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerNoReturnParallel()
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
                  this.nonBlockClientNoReturn(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncThreadedSelectorServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("noReturn", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFastFramedClientSyncNonblockingServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingFastServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFastFramedClientSyncNonblockingFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingFastServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientNoReturn(port);
    }
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerNoReturnParallel()
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
                  this.syncFramedClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncNonblockingServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFastFramedClientSyncHsHaServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncHsHaFastServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFastFramedClientSyncHsHaFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startSyncHsHaFastServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientNoReturn(port);
    }

    this.waitAndAssertTracesClientSyncServerSync("noReturn", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerNoReturnParallel()
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
                  this.syncFramedClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncHsHaServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("noReturn", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 1);
  }

  @Test
  public void syncFastFramedClientAsyncNonblockingServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientNoReturn(port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 5);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerNoReturnParallel()
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
                  this.syncFramedClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncNonblockingServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 1);
  }

  @Test
  public void syncFastFramedClientAsyncHsHaServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaFastServer(port);
    this.syncFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 1);
  }

  @Test
  public void syncFastFramedClientAsyncHsHaFastServerNoReturn() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaFastServer(port);
    this.syncFastFramedClientNoReturn(port);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerNoReturnMuti() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientNoReturn(port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("noReturn", 5);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerNoReturnParallel()
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
                  this.syncFramedClientNoReturn(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncHsHaServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("noReturn", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerSync("noReturn", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerNoReturnMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientNoReturn(port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("noReturn", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerNoReturnParallel()
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
                  this.nonBlockClientNoReturn(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncNonblockingServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("noReturn", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerSync("noReturn", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerNoReturnMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientNoReturn(port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("noReturn", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerNoReturnParallel()
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
                  this.nonBlockClientNoReturn(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncHsHaServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("noReturn", threadCount);
  }

  @Test
  public void syncClientAsyncNonblockingServerNoReturnError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startAsyncNonblockingServer(port);
      this.syncClientNoReturn(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("noReturn", 1);
  }

  @Test
  public void syncClientSyncNonblockingServerNoReturnError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startSyncNonblockingServer(port);
      this.syncClientNoReturn(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("noReturn", 1);
  }

  @Test
  public void syncClientAsyncHsHaServerNoReturnError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startAsyncHsHaServer(port);
      this.syncClientNoReturn(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("noReturn", 1);
  }

  @Test
  public void syncClientSyncHsHaServerNoReturnError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startSyncHsHaServer(port);
      this.syncClientNoReturn(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("noReturn", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerAsync("noReturn", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerNoReturnMuti() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientNoReturn(port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("noReturn", 5);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerNoReturnParallel()
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
                  this.nonBlockClientNoReturn(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncNonblockingServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("noReturn", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerNoReturn() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.nonBlockClientNoReturn(port);
    this.waitAndAssertTracesClientAsyncServerAsync("noReturn", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerNoReturnMuti() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientNoReturn(port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("noReturn", 5);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerNoReturnParallel()
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
                  this.nonBlockClientNoReturn(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncHsHaServerNoReturnParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("noReturn", threadCount);
  }

  public void syncClientNoReturn(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.noReturn(1));
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncClientMultiNoReturn(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      this.testing().runWithSpan("parent", () -> client.noReturn(1));
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void nonBlockClientNoReturn(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<ThriftService.AsyncClient.noReturn_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.noReturn_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.noReturn_call s) {}

          @Override
          public void onError(Exception e) {
            Assertions.assertThat(e.getCause().getMessage())
                .isEqualTo("Read call frame size failed");
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.noReturn(1, callback));
  }

  public void syncFramedClientNoReturn(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.noReturn(1));
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncFastFramedClientNoReturn(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFastFramedTransport framedTransport = new TFastFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      this.testing().runWithSpan("parent", () -> client.noReturn(1));
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncFramedClientMultiNoReturn(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      this.testing().runWithSpan("parent", () -> client.noReturn(1));
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncNonblockingSaslClientNoReturn(int port) throws TException, IOException {
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
      this.testing().runWithSpan("parent", () -> client.noReturn(1));
    } catch (Exception e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
  }
}

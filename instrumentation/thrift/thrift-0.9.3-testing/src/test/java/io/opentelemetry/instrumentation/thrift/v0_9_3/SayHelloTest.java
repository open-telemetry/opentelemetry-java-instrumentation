/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_9_3;

import com.google.common.base.VerifyException;
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
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class SayHelloTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerSayHello() throws TException {
    this.startSyncSimpleServer(this.port);
    this.syncClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncClientSyncSimpleServerSayHelloMuti() throws TException {
    this.startSyncSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientSayHello(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 5);
  }

  @Test
  public void syncClientSyncSimpleServerSayHelloParallel() throws TException, InterruptedException {
    this.startSyncSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientSayHello(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerSayHelloParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerSayHello() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientSayHello(port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerSayHelloMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientSayHello(port);
    }
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 5);
  }

  @Test
  public void syncClientSyncThreadPoolServerSayHelloParallel()
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
                  this.syncClientSayHello(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerSayHelloParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerSayHello() throws TException {
    this.startMultiSimpleServer(this.port);
    this.syncClientMultiSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", "syncHelloWorld:sayHello", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerSayHelloMuti() throws TException {
    this.startMultiSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiSayHello(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSync("sayHello", "syncHelloWorld:sayHello", 5);
  }

  @Test
  public void syncClientMutiSyncSimpleServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startMultiSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientMultiSayHello(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerSayHelloParallel field: " + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(
        "sayHello", "syncHelloWorld:sayHello", threadCount);
  }

  @Test
  public void syncClientSyncThreadedSelectorServerSayHelloError() {
    Exception error = null;
    try {
      this.startSyncThreadedSelectorServer(this.port);
      this.syncClientSayHello(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("sayHello", 1);
  }

  @Test
  public void syncClientAsyncThreadedSelectorServerSayHelloError() {
    Exception error = null;
    try {
      this.startAsyncThreadedSelectorServer(this.port);
      this.syncClientSayHello(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("sayHello", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerSayHello() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.syncFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 1);
  }

  @Test
  public void syncFastFramedClientAsyncThreadedSelectorServerSayHello() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.syncFastFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorFastServerSayHello() throws TException {
    this.startAsyncThreadedSelectorFastServer(this.port);
    this.syncFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 1);
  }

  @Test
  public void syncFastFramedClientAsyncThreadedSelectorFastServerSayHello() throws TException {
    this.startAsyncThreadedSelectorFastServer(this.port);
    this.syncFastFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerSayHelloMuti() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientSayHello(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 5);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientSayHello(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerSayHello() throws TException {
    this.startMultiThreadedSelectorServer(this.port);
    this.syncFramedClientMultiSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", "syncHelloWorld:sayHello", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerSayHelloMuti() throws TException {
    this.startMultiThreadedSelectorServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiSayHello(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSync("sayHello", "syncHelloWorld:sayHello", 5);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startMultiThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientMultiSayHello(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerSayHelloParallel field: "
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
        "sayHello", "syncHelloWorld:sayHello", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerSayHello() throws TException {
    this.startSyncThreadedSelectorServer(this.port);
    this.syncFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFastFramedClientSyncThreadedSelectorServerSayHello() throws TException {
    this.startSyncThreadedSelectorServer(this.port);
    this.syncFastFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorFastServerSayHello() throws TException {
    this.startSyncThreadedSelectorFastServer(this.port);
    this.syncFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFastFramedClientSyncThreadedSelectorFastServerSayHello() throws TException {
    this.startSyncThreadedSelectorFastServer(this.port);
    this.syncFastFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerSayHelloMuti() throws TException {
    this.startSyncThreadedSelectorServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientSayHello(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 5);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientSayHello(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncThreadedSelectorServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerSayHello() throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.nonBlockClientSayHello(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("sayHello", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerSayHelloMuti()
      throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientSayHello(this.port);
    }
    this.waitAndAssertTracesClientAsyncServerAsync("sayHello", 5);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientSayHello(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncThreadedSelectorServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("sayHello", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerSayHello() throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);
    this.nonBlockClientSayHello(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("sayHello", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerSayHelloMuti()
      throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientSayHello(this.port);
    }
    this.waitAndAssertTracesClientAsyncServerSync("sayHello", 5);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientSayHello(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncThreadedSelectorServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("sayHello", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerSayHello() throws TException {
    this.startSyncNonblockingServer(this.port);
    this.syncFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFastFramedClientSyncNonblockingServerSayHello() throws TException {
    this.startSyncNonblockingServer(this.port);
    this.syncFastFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingFastServerSayHello() throws TException {
    this.startSyncNonblockingFastServer(this.port);
    this.syncFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFastFramedClientSyncNonblockingFastServerSayHello() throws TException {
    this.startSyncNonblockingFastServer(this.port);
    this.syncFastFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerSayHelloMuti() throws TException {
    this.startSyncNonblockingServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientSayHello(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientSayHello(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncNonblockingServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerSayHello() throws TException {
    this.startSyncHsHaServer(this.port);
    this.syncFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFastFramedClientSyncHsHaServerSayHello() throws TException {
    this.startSyncHsHaServer(this.port);
    this.syncFastFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaFastServerSayHello() throws TException {
    this.startSyncHsHaFastServer(this.port);
    this.syncFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFastFramedClientSyncHsHaFastServerSayHello() throws TException {
    this.startSyncHsHaFastServer(this.port);
    this.syncFastFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerSayHelloMuti() throws TException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientSayHello(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("sayHello", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientSayHello(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncHsHaServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("sayHello", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerSayHello() throws TException {
    this.startAsyncNonblockingServer(this.port);
    this.syncFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 1);
  }

  @Test
  public void syncFastFramedClientAsyncNonblockingServerSayHello() throws TException {
    this.startAsyncNonblockingServer(this.port);
    this.syncFastFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerSayHelloMuti() throws TException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientSayHello(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 5);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientSayHello(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncNonblockingServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerSayHello() throws TException {
    this.startAsyncHsHaServer(this.port);
    this.syncFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 1);
  }

  @Test
  public void syncFastFramedClientAsyncHsHaServerSayHello() throws TException {
    this.startAsyncHsHaServer(this.port);
    this.syncFastFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaFastServerSayHello() throws TException {
    this.startAsyncHsHaFastServer(this.port);
    this.syncFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 1);
  }

  @Test
  public void syncFastFramedClientAsyncHsHaFastServerSayHello() throws TException {
    this.startAsyncHsHaFastServer(this.port);
    this.syncFastFramedClientSayHello(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerSayHelloMuti() throws TException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientSayHello(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("sayHello", 5);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientSayHello(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncHsHaServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("sayHello", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerSayHello() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);
    this.nonBlockClientSayHello(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("sayHello", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerSayHelloMuti() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientSayHello(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("sayHello", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientSayHello(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncNonblockingServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("sayHello", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerSayHello() throws TException, IOException {
    this.startSyncHsHaServer(this.port);
    this.nonBlockClientSayHello(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("sayHello", 1);
  }

  @Test
  public void nonBlockMultiClientSyncHsHaServerMultiSayHello() throws TException, IOException {
    this.startMultiSyncHsHaServer(this.port);
    this.nonBlockClientMultiSayHello(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("sayHello", "syncHelloWorld:sayHello", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerSayHelloMuti() throws TException, IOException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientSayHello(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("sayHello", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientSayHello(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncHsHaServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("sayHello", threadCount);
  }

  @Test
  public void syncClientAsyncNonblockingServerSayHelloError() {
    Exception error = null;
    try {
      this.startAsyncNonblockingServer(this.port);
      this.syncClientSayHello(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("sayHello", 1);
  }

  @Test
  public void syncClientSyncNonblockingServerSayHelloError() {
    Exception error = null;
    try {
      this.startSyncNonblockingServer(this.port);
      this.syncClientSayHello(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("sayHello", 1);
  }

  @Test
  public void syncClientAsyncHsHaServerSayHelloError() {
    Exception error = null;
    try {
      this.startAsyncHsHaServer(this.port);
      this.syncClientSayHello(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("sayHello", 1);
  }

  @Test
  public void syncClientSyncHsHaServerSayHelloError() {
    Exception error = null;
    try {
      this.startSyncHsHaServer(this.port);
      this.syncClientSayHello(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("sayHello", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerSayHello() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);
    this.nonBlockClientSayHello(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("sayHello", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerSayHelloMuti() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientSayHello(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("sayHello", 5);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientSayHello(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncNonblockingServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("sayHello", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerSayHello() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);
    this.nonBlockClientSayHello(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("sayHello", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerSayHelloMuti() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientSayHello(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("sayHello", 5);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerSayHelloParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientSayHello(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncHsHaServerSayHelloParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("sayHello", threadCount);
  }

  public void syncClientSayHello(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      String response = this.testing().runWithSpan("parent", () -> client.sayHello("US", "Bob"));
      Assertions.assertThat(response).isEqualTo("Hello USs' Bob");
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncClientMultiSayHello(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      String response = this.testing().runWithSpan("parent", () -> client.sayHello("US", "Bob"));
      Assertions.assertThat(response).isEqualTo("Hello USs' Bob");
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void nonBlockClientSayHello(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<ThriftService.AsyncClient.sayHello_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.sayHello_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.sayHello_call s) {
            try {
              String result = s.getResult();
              Assertions.assertThat(result).isEqualTo("Hello USs' Bob");
            } catch (TException e) {
              throw new VerifyException(e);
            }
          }

          @Override
          public void onError(Exception e) {
            Assertions.assertThat(e.getCause().getMessage())
                .isEqualTo("Read call frame size failed");
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.sayHello("US", "Bob", callback));
  }

  public void nonBlockClientMultiSayHello(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    TMultiplexedProtocolFactory multiplexedProtocolFactory =
        new TMultiplexedProtocolFactory(protocolFactory, "syncHelloWorld");
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, multiplexedProtocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<ThriftService.AsyncClient.sayHello_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.sayHello_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.sayHello_call s) {
            try {
              String result = s.getResult();
              Assertions.assertThat(result).isEqualTo("Hello USs' Bob");
            } catch (TException e) {
              throw new VerifyException(e);
            }
          }

          @Override
          public void onError(Exception e) {
            Assertions.assertThat(e.getCause().getMessage())
                .isEqualTo("Read call frame size failed");
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.sayHello("US", "Bob", callback));
  }

  public void syncFramedClientSayHello(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      String response = this.testing().runWithSpan("parent", () -> client.sayHello("US", "Bob"));
      Assertions.assertThat(response).isEqualTo("Hello USs' Bob");
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncFastFramedClientSayHello(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFastFramedTransport framedTransport = new TFastFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      String response = this.testing().runWithSpan("parent", () -> client.sayHello("US", "Bob"));
      Assertions.assertThat(response).isEqualTo("Hello USs' Bob");
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncFramedClientMultiSayHello(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      String response = this.testing().runWithSpan("parent", () -> client.sayHello("US", "Bob"));
      Assertions.assertThat(response).isEqualTo("Hello USs' Bob");
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncNonblockingSaslClientSayHello(int port) throws TException, IOException {
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
      this.testing().runWithSpan("parent", () -> client.sayHello("US", "Bob"));
    } catch (Exception e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
  }
}

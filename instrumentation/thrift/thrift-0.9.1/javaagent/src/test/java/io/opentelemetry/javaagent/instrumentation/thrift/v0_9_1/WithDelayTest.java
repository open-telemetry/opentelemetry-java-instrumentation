/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1;

import com.google.common.base.VerifyException;
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
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class WithDelayTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerSayHello() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientWithDelay(port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 1);
  }

  @Test
  public void syncClientSyncSimpleServerSayHelloMuti() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithDelay(port, 2);
    }
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 5);
  }

  @Test
  public void syncClientSyncSimpleServerSayHelloParallel() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientSyncServerSync("withDelay", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerSayHello() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientWithDelay(port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerSayHelloMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithDelay(port, 2);
    }
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 5);
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
                  this.syncClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientSyncServerSync("withDelay", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerSayHello() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    this.syncClientMultiWithDelay(port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", "syncHelloWorld:withDelay", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerSayHelloMuti() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiWithDelay(port, 2);
    }
    this.waitAndAssertTracesClientSyncServerSync("withDelay", "syncHelloWorld:withDelay", 5);
  }

  @Test
  public void syncClientMutiSyncSimpleServerSayHelloParallel()
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
                  this.syncClientMultiWithDelay(port, 2);
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
        "withDelay", "syncHelloWorld:withDelay", threadCount);
  }

  @Test
  public void syncClientSyncThreadedSelectorServerSayHelloError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startSyncThreadedSelectorServer(port);
      this.syncClientWithDelay(port, 2);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withDelay", 1);
  }

  @Test
  public void syncClientAsyncThreadedSelectorServerSayHelloError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startAsyncThreadedSelectorServer(port);
      this.syncClientWithDelay(port, 2);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withDelay", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerSayHello() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.syncFramedClientWithDelay(port, 2);
    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerSayHelloMuti() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 5);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerSayHelloParallel()
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
                  this.syncFramedClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientSyncServerAsync("withDelay", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerSayHello() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    this.syncFramedClientMultiWithDelay(port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", "syncHelloWorld:withDelay", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerSayHelloMuti() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientSyncServerSync("withDelay", "syncHelloWorld:withDelay", 5);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerSayHelloParallel()
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
                  this.syncFramedClientMultiWithDelay(port, 2);
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
        "withDelay", "syncHelloWorld:withDelay", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerSayHello() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.syncFramedClientWithDelay(port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerSayHelloMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientSyncServerSync("withDelay", 5);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerSayHelloParallel()
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
                  this.syncFramedClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientSyncServerSync("withDelay", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.nonBlockClientWithDelay(port, 2);
    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerSayHelloMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 5);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerSayHelloParallel()
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
                  this.nonBlockClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.nonBlockClientWithDelay(port, 2);
    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerSayHelloMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 5);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerSayHelloParallel()
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
                  this.nonBlockClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientAsyncServerSync("withDelay", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerSayHello() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientWithDelay(port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerSayHelloMuti() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientSyncServerSync("withDelay", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerSayHelloParallel()
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
                  this.syncFramedClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientSyncServerSync("withDelay", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerSayHello() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientWithDelay(port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerSayHelloMuti() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientSyncServerSync("withDelay", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerSayHelloParallel()
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
                  this.syncFramedClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientSyncServerSync("withDelay", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerSayHello() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.syncFramedClientWithDelay(port, 2);
    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerSayHelloMuti() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 5);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerSayHelloParallel()
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
                  this.syncFramedClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientSyncServerAsync("withDelay", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerSayHello() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.syncFramedClientWithDelay(port, 2);
    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerSayHelloMuti() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 5);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerSayHelloParallel()
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
                  this.syncFramedClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientSyncServerAsync("withDelay", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientWithDelay(port, 2);
    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerSayHelloMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(port, 1);
    }

    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerSayHelloParallel()
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
                  this.nonBlockClientWithDelay(port, 1);
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
    this.waitAndAssertTracesClientAsyncServerSync("withDelay", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientWithDelay(port, 2);
    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerSayHelloMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerSayHelloParallel()
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
                  this.nonBlockClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientAsyncServerSync("withDelay", threadCount);
  }

  @Test
  public void syncClientAsyncNonblockingServerSayHelloError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startAsyncNonblockingServer(port);
      this.syncClientWithDelay(port, 2);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withDelay", 1);
  }

  @Test
  public void syncClientSyncNonblockingServerSayHelloError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startSyncNonblockingServer(port);
      this.syncClientWithDelay(port, 2);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withDelay", 1);
  }

  @Test
  public void syncClientAsyncHsHaServerSayHelloError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startAsyncHsHaServer(port);
      this.syncClientWithDelay(port, 2);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withDelay", 1);
  }

  @Test
  public void syncClientSyncHsHaServerSayHelloError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startSyncHsHaServer(port);
      this.syncClientWithDelay(port, 2);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withDelay", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.nonBlockClientWithDelay(port, 2);
    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerSayHelloMuti() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 5);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerSayHelloParallel()
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
                  this.nonBlockClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.nonBlockClientWithDelay(port, 2);
    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerSayHelloMuti() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(port, 2);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 5);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerSayHelloParallel()
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
                  this.nonBlockClientWithDelay(port, 2);
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
    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", threadCount);
  }

  public void syncClientWithDelay(int port, int delay) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      String response = this.testing().runWithSpan("parent", () -> client.withDelay(delay));
      Assertions.assertThat(response).isEqualTo("delay " + delay);
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncClientMultiWithDelay(int port, int delay) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      String response = this.testing().runWithSpan("parent", () -> client.withDelay(delay));
      Assertions.assertThat(response).isEqualTo("delay " + delay);
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void nonBlockClientWithDelay(int port, int delay) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<ThriftService.AsyncClient.withDelay_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.withDelay_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.withDelay_call s) {
            try {
              String result = s.getResult();
              Assertions.assertThat(result).isEqualTo("delay " + delay);
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
    this.testing().runWithSpan("parent", () -> asyClient.withDelay(delay, callback));
  }

  public void syncFramedClientWithDelay(int port, int delay) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      String response = this.testing().runWithSpan("parent", () -> client.withDelay(delay));
      Assertions.assertThat(response).isEqualTo("delay " + delay);
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncFramedClientMultiWithDelay(int port, int delay) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      String response = this.testing().runWithSpan("parent", () -> client.withDelay(delay));
      Assertions.assertThat(response).isEqualTo("delay " + delay);
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncNonblockingSaslClientWithDelay(int port, int delay) throws IOException {
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
      this.testing().runWithSpan("parent", () -> client.withDelay(delay));
    } catch (Exception e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
  }
}

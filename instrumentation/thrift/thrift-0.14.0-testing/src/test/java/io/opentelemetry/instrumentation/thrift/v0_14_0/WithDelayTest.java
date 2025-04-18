/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_14_0;

import com.google.common.base.VerifyException;
import io.opentelemetry.instrumentation.thrift.v0_14_0.thrift.ThriftService;
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
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class WithDelayTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerSayHello() throws TException {
    this.startSyncSimpleServer(this.port);
    this.syncClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 1);
  }

  @Test
  public void syncClientSyncSimpleServerSayHelloMuti() throws TException {
    this.startSyncSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithDelay(this.port, 2);
    }
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 5);
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
                  this.syncClientWithDelay(this.port, 2);
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
    this.startMultiSimpleServer(this.port);
    this.syncClientMultiWithDelay(this.port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", "syncHelloWorld:withDelay", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerSayHelloMuti() throws TException {
    this.startMultiSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiWithDelay(this.port, 2);
    }
    this.waitAndAssertTracesClientSyncServerSync("withDelay", "syncHelloWorld:withDelay", 5);
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
                  this.syncClientMultiWithDelay(this.port, 2);
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
      this.startSyncThreadedSelectorServer(this.port);
      this.syncClientWithDelay(this.port, 2);
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
      this.startAsyncThreadedSelectorServer(this.port);
      this.syncClientWithDelay(this.port, 2);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withDelay", 1);
  }

  @Test
  public void syncNonblockingSaslClientSyncSaslNonblockingServerSayHelloError() {
    Exception error = null;
    try {
      this.startSyncSaslNonblockingServer(this.port);
      this.syncNonblockingSaslClientWithDelay(this.port, 2);
    } catch (IOException | TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerNoSasl("withDelay", 1);
  }

  @Test
  public void syncNonblockingSaslClientAsyncSaslNonblockingServerSayHelloError() {
    Exception error = null;
    try {
      this.startAsyncSaslNonblockingServer(this.port);
      this.syncNonblockingSaslClientWithDelay(this.port, 2);
    } catch (IOException | TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerNoSasl("withDelay", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerSayHello() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.syncFramedClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerSayHelloMuti() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(this.port, 2);
    }

    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 5);
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
                  this.syncFramedClientWithDelay(this.port, 2);
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
    this.startMultiThreadedSelectorServer(this.port);
    this.syncFramedClientMultiWithDelay(this.port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", "syncHelloWorld:withDelay", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerSayHelloMuti() throws TException {
    this.startMultiThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiWithDelay(this.port, 2);
    }

    this.waitAndAssertTracesClientSyncServerSync("withDelay", "syncHelloWorld:withDelay", 5);
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
                  this.syncFramedClientMultiWithDelay(this.port, 2);
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
    this.startSyncThreadedSelectorServer(this.port);
    this.syncFramedClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerSayHelloMuti() throws TException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(this.port, 2);
    }

    this.waitAndAssertTracesClientSyncServerSync("withDelay", 5);
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
                  this.syncFramedClientWithDelay(this.port, 2);
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
    this.startAsyncThreadedSelectorServer(this.port);
    this.nonBlockClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerSayHelloMuti()
      throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(this.port, 2);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 5);
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
                  this.nonBlockClientWithDelay(this.port, 2);
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
    this.startSyncThreadedSelectorServer(this.port);
    this.nonBlockClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerSayHelloMuti()
      throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(this.port, 2);
    }

    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 5);
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
                  this.nonBlockClientWithDelay(this.port, 2);
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
    this.startSyncNonblockingServer(this.port);
    this.syncFramedClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerSayHelloMuti() throws TException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(this.port, 2);
    }

    this.waitAndAssertTracesClientSyncServerSync("withDelay", 5);
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
                  this.syncFramedClientWithDelay(this.port, 2);
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
    this.startSyncHsHaServer(this.port);
    this.syncFramedClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientSyncServerSync("withDelay", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerSayHelloMuti() throws TException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(this.port, 2);
    }

    this.waitAndAssertTracesClientSyncServerSync("withDelay", 5);
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
                  this.syncFramedClientWithDelay(this.port, 2);
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
    this.startAsyncNonblockingServer(this.port);
    this.syncFramedClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerSayHelloMuti() throws TException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(this.port, 2);
    }

    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 5);
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
                  this.syncFramedClientWithDelay(this.port, 2);
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
    this.startAsyncHsHaServer(this.port);
    this.syncFramedClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerSayHelloMuti() throws TException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithDelay(this.port, 2);
    }

    this.waitAndAssertTracesClientSyncServerAsync("withDelay", 5);
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
                  this.syncFramedClientWithDelay(this.port, 2);
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
    this.startSyncNonblockingServer(this.port);
    this.nonBlockClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerSayHelloMuti() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(this.port, 1);
    }

    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 5);
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
                  this.nonBlockClientWithDelay(this.port, 1);
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
    this.startSyncHsHaServer(this.port);
    this.nonBlockClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerSayHelloMuti() throws TException, IOException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(this.port, 2);
    }

    this.waitAndAssertTracesClientAsyncServerSync("withDelay", 5);
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
                  this.nonBlockClientWithDelay(this.port, 2);
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
      this.startAsyncNonblockingServer(this.port);
      this.syncClientWithDelay(this.port, 2);
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
      this.startSyncNonblockingServer(this.port);
      this.syncClientWithDelay(this.port, 2);
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
      this.startAsyncHsHaServer(this.port);
      this.syncClientWithDelay(this.port, 2);
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
      this.startSyncHsHaServer(this.port);
      this.syncClientWithDelay(this.port, 2);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withDelay", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerSayHello() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);
    this.nonBlockClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerSayHelloMuti() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(this.port, 1);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 5);
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
                  this.nonBlockClientWithDelay(this.port, 1);
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
    this.startAsyncHsHaServer(this.port);
    this.nonBlockClientWithDelay(this.port, 2);
    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerSayHelloMuti() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithDelay(this.port, 2);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("withDelay", 5);
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
                  this.nonBlockClientWithDelay(this.port, 2);
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
    try (TTransport transport = new TSocket("localhost", port)) {
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      String response = this.testing().runWithSpan("parent", () -> client.withDelay(delay));
      Assertions.assertThat(response).isEqualTo("delay " + delay);
    }
  }

  public void syncClientMultiWithDelay(int port, int delay) throws TException {
    try (TTransport transport = new TSocket("localhost", port)) {
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      this.testing().runWithSpan("parent", () -> client.withDelay(delay));
    }
  }

  public void nonBlockClientWithDelay(int port, int delay) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<String> callback =
        new AsyncMethodCallback<String>() {
          @Override
          public void onComplete(String s) {
            Assertions.assertThat(s).isEqualTo("delay " + delay);
          }

          @Override
          public void onError(Exception e) {
            throw new VerifyException("nonBlockClientWithDelay test failed", e);
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.withDelay(delay, callback));
  }

  public void syncFramedClientWithDelay(int port, int delay) throws TException {
    TFramedTransport framedTransport = new TFramedTransport(new TSocket("localhost", port));
    framedTransport.open();
    TProtocol protocol = new TBinaryProtocol(framedTransport);
    ThriftService.Client client = new ThriftService.Client(protocol);
    String response = this.testing().runWithSpan("parent", () -> client.withDelay(delay));
    Assertions.assertThat(response).isEqualTo("delay " + delay);
  }

  public void syncFramedClientMultiWithDelay(int port, int delay) throws TException {
    TFramedTransport framedTransport = new TFramedTransport(new TSocket("localhost", port));
    framedTransport.open();
    TProtocol protocol = new TBinaryProtocol(framedTransport);
    TMultiplexedProtocol multiplexedProtocol = new TMultiplexedProtocol(protocol, "syncHelloWorld");
    ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
    String response = this.testing().runWithSpan("parent", () -> client.withDelay(delay));
    Assertions.assertThat(response).isEqualTo("delay " + delay);
  }

  public void syncNonblockingSaslClientWithDelay(int port, int delay)
      throws TException, IOException {

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

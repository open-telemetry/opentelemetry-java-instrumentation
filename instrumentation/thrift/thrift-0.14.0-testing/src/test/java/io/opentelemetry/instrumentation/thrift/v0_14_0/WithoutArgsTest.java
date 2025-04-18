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

public class WithoutArgsTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerWithoutArgs() throws TException {
    this.startSyncSimpleServer(this.port);
    this.syncClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 1);
  }

  @Test
  public void syncClientSyncSimpleServerWithoutArgsMuti() throws TException {
    this.startSyncSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithoutArgs(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 5);
  }

  @Test
  public void syncClientSyncSimpleServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startSyncSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientWithoutArgs(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithoutArgs() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientWithoutArgs(port);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithoutArgsMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithoutArgs(port);
    }
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 5);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithoutArgsParallel()
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
                  this.syncClientWithoutArgs(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithoutArgs() throws TException {
    this.startMultiSimpleServer(this.port);
    this.syncClientMultiWithoutArgs(this.port);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", "syncHelloWorld:withoutArgs", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithoutArgsMuti() throws TException {
    this.startMultiSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiWithoutArgs(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", "syncHelloWorld:withoutArgs", 5);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startMultiSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientMultiWithoutArgs(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(
        "withoutArgs", "syncHelloWorld:withoutArgs", threadCount);
  }

  @Test
  public void syncClientSyncThreadedSelectorServerWithoutArgsError() {
    Exception error = null;
    try {
      this.startSyncThreadedSelectorServer(this.port);
      this.syncClientWithoutArgs(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withoutArgs", 1);
  }

  @Test
  public void syncClientAsyncThreadedSelectorServerWithoutArgsError() {
    Exception error = null;
    try {
      this.startAsyncThreadedSelectorServer(this.port);
      this.syncClientWithoutArgs(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withoutArgs", 1);
  }

  @Test
  public void syncNonblockingSaslClientSyncSaslNonblockingServerWithoutArgsError() {
    Exception error = null;
    try {
      this.startSyncSaslNonblockingServer(this.port);
      this.syncNonblockingSaslClientWithoutArgs(this.port);
    } catch (IOException | TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerNoSasl("withoutArgs", 1);
  }

  @Test
  public void syncNonblockingSaslClientAsyncSaslNonblockingServerWithoutArgsError() {
    Exception error = null;
    try {
      this.startAsyncSaslNonblockingServer(this.port);
      this.syncNonblockingSaslClientWithoutArgs(this.port);
    } catch (IOException | TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServerNoSasl("withoutArgs", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithoutArgs() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.syncFramedClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("withoutArgs", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithoutArgsMuti() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("withoutArgs", 5);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithoutArgs(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("withoutArgs", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithoutArgs() throws TException {
    this.startMultiThreadedSelectorServer(this.port);
    this.syncFramedClientMultiWithoutArgs(this.port);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", "syncHelloWorld:withoutArgs", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithoutArgsMuti() throws TException {
    this.startMultiThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", "syncHelloWorld:withoutArgs", 5);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startMultiThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientMultiWithoutArgs(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerWithoutArgsParallel field: "
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
        "withoutArgs", "syncHelloWorld:withoutArgs", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithoutArgs() throws TException {
    this.startSyncThreadedSelectorServer(this.port);
    this.syncFramedClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithoutArgsMuti() throws TException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 5);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithoutArgs(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncThreadedSelectorServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithoutArgs()
      throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.nonBlockClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("withoutArgs", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithoutArgsMuti()
      throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("withoutArgs", 5);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithoutArgs(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncThreadedSelectorServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("withoutArgs", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithoutArgs() throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);
    this.nonBlockClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithoutArgsMuti()
      throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", 5);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithoutArgs(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncThreadedSelectorServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithoutArgs() throws TException {
    this.startSyncNonblockingServer(this.port);
    this.syncFramedClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithoutArgsMuti() throws TException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithoutArgs(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncNonblockingServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithoutArgs() throws TException {
    this.startSyncHsHaServer(this.port);
    this.syncFramedClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithoutArgsMuti() throws TException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithoutArgs(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncHsHaServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithoutArgs() throws TException {
    this.startAsyncNonblockingServer(this.port);
    this.syncFramedClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("withoutArgs", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithoutArgsMuti() throws TException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("withoutArgs", 5);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithoutArgs(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncNonblockingServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("withoutArgs", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithoutArgs() throws TException {
    this.startAsyncHsHaServer(this.port);
    this.syncFramedClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("withoutArgs", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithoutArgsMuti() throws TException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("withoutArgs", 5);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithoutArgs(this.port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncHsHaServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync("withoutArgs", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithoutArgs() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);
    this.nonBlockClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithoutArgsMuti() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithoutArgs(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncNonblockingServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithoutArgs() throws TException, IOException {
    this.startSyncHsHaServer(this.port);
    this.nonBlockClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithoutArgsMuti() throws TException, IOException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithoutArgs(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncHsHaServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", threadCount);
  }

  @Test
  public void syncClientAsyncNonblockingServerWithoutArgsError() {
    Exception error = null;
    try {
      this.startAsyncNonblockingServer(this.port);
      this.syncClientWithoutArgs(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withoutArgs", 1);
  }

  @Test
  public void syncClientSyncNonblockingServerWithoutArgsError() {
    Exception error = null;
    try {
      this.startSyncNonblockingServer(this.port);
      this.syncClientWithoutArgs(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withoutArgs", 1);
  }

  @Test
  public void syncClientAsyncHsHaServerWithoutArgsError() {
    Exception error = null;
    try {
      this.startAsyncHsHaServer(this.port);
      this.syncClientWithoutArgs(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withoutArgs", 1);
  }

  @Test
  public void syncClientSyncHsHaServerWithoutArgsError() {
    Exception error = null;
    try {
      this.startSyncHsHaServer(this.port);
      this.syncClientWithoutArgs(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withoutArgs", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithoutArgs() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);
    this.nonBlockClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("withoutArgs", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithoutArgsMuti() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("withoutArgs", 5);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithoutArgs(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncNonblockingServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("withoutArgs", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithoutArgs() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);
    this.nonBlockClientWithoutArgs(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("withoutArgs", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithoutArgsMuti() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithoutArgs(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("withoutArgs", 5);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithoutArgsParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithoutArgs(this.port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncHsHaServerWithoutArgsParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync("withoutArgs", threadCount);
  }

  public void syncClientWithoutArgs(int port) throws TException {
    try (TTransport transport = new TSocket("localhost", port)) {
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      String response = this.testing().runWithSpan("parent", () -> client.withoutArgs());
      Assertions.assertThat(response).isEqualTo("no args");
    }
  }

  public void syncClientMultiWithoutArgs(int port) throws TException {
    try (TTransport transport = new TSocket("localhost", port)) {
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      String response = this.testing().runWithSpan("parent", () -> client.withoutArgs());
      Assertions.assertThat(response).isEqualTo("no args");
    }
  }

  public void nonBlockClientWithoutArgs(int port) throws TException, IOException {
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
            Assertions.assertThat(s).isEqualTo("no args");
          }

          @Override
          public void onError(Exception e) {
            throw new VerifyException("asyncClientWithoutArgs test failed", e);
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.withoutArgs(callback));
  }

  public void syncFramedClientWithoutArgs(int port) throws TException {
    TFramedTransport framedTransport = new TFramedTransport(new TSocket("localhost", port));
    framedTransport.open();
    TProtocol protocol = new TBinaryProtocol(framedTransport);
    ThriftService.Client client = new ThriftService.Client(protocol);
    String response = this.testing().runWithSpan("parent", () -> client.withoutArgs());
    Assertions.assertThat(response).isEqualTo("no args");
  }

  public void syncFramedClientMultiWithoutArgs(int port) throws TException {
    TFramedTransport framedTransport = new TFramedTransport(new TSocket("localhost", port));
    framedTransport.open();
    TProtocol protocol = new TBinaryProtocol(framedTransport);
    TMultiplexedProtocol multiplexedProtocol = new TMultiplexedProtocol(protocol, "syncHelloWorld");
    ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
    String response = this.testing().runWithSpan("parent", () -> client.withoutArgs());
    Assertions.assertThat(response).isEqualTo("no args");
  }

  public void syncNonblockingSaslClientWithoutArgs(int port) throws TException, IOException {
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
      this.testing().runWithSpan("parent", () -> client.withoutArgs());
    } catch (Exception e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
  }
}

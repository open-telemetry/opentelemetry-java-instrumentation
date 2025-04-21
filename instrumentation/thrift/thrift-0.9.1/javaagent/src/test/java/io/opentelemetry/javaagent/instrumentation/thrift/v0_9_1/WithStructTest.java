/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1;

import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift.Account;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift.ThriftService;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift.User;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift.UserAccount;
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
import org.testcontainers.shaded.com.google.common.base.VerifyException;

public class WithStructTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerWithStruct() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientWithStruct(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", 1);
  }

  @Test
  public void syncClientSyncSimpleServerWithStructMuti() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithStruct(port);
    }
    this.waitAndAssertTracesClientSyncServerSync(port, "data", 5);
  }

  @Test
  public void syncClientSyncSimpleServerWithStructParallel()
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
                  this.syncClientWithStruct(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithStruct() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientWithStruct(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithStructMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithStruct(port);
    }
    this.waitAndAssertTracesClientSyncServerSync(port, "data", 5);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithStructParallel()
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
                  this.syncClientWithStruct(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithStruct() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    this.syncClientMultiWithStruct(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", "syncHelloWorld:data", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithStructMuti() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiWithStruct(port);
    }
    this.waitAndAssertTracesClientSyncServerSync(port, "data", "syncHelloWorld:data", 5);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithStructParallel()
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
                  this.syncClientMultiWithStruct(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncClientSimpleServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", "syncHelloWorld:data", threadCount);
  }

  @Test
  public void syncClientSyncThreadedSelectorServerWithStructError() {
    Exception error = null;
    int port = super.getPort();
    try {
      this.startSyncThreadedSelectorServer(port);
      this.syncClientWithStruct(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer(port, "data", 1);
  }

  @Test
  public void syncClientAsyncThreadedSelectorServerWithStructError() {
    Exception error = null;
    int port = super.getPort();
    try {
      this.startAsyncThreadedSelectorServer(port);
      this.syncClientWithStruct(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer(port, "data", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithStruct() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.syncFramedClientWithStruct(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "data", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithStructMuti() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(port);
    }

    this.waitAndAssertTracesClientSyncServerAsync(port, "data", 5);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithStructParallel()
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
                  this.syncFramedClientWithStruct(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync(port, "data", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithStruct() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    this.syncFramedClientMultiWithStruct(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", "syncHelloWorld:data", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithStructMuti() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiWithStruct(port);
    }

    this.waitAndAssertTracesClientSyncServerSync(port, "data", "syncHelloWorld:data", 5);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithStructParallel()
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
                  this.syncFramedClientMultiWithStruct(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncThreadedSelectorServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", "syncHelloWorld:data", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithStruct() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.syncFramedClientWithStruct(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithStructMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(port);
    }

    this.waitAndAssertTracesClientSyncServerSync(port, "data", 5);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithStructParallel()
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
                  this.syncFramedClientWithStruct(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncThreadedSelectorServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithStruct() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.nonBlockClientWithStruct(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "data", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithStructMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync(port, "data", 5);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithStructParallel()
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
                  this.nonBlockClientWithStruct(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncThreadedSelectorServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "data", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithStruct() throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.nonBlockClientWithStruct(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "data", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithStructMuti()
      throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(port);
    }

    this.waitAndAssertTracesClientAsyncServerSync(port, "data", 5);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithStructParallel()
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
                  this.nonBlockClientWithStruct(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncThreadedSelectorServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync(port, "data", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithStruct() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientWithStruct(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithStructMuti() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(port);
    }

    this.waitAndAssertTracesClientSyncServerSync(port, "data", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithStructParallel()
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
                  this.syncFramedClientWithStruct(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncNonblockingServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithStruct() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientWithStruct(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithStructMuti() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(port);
    }

    this.waitAndAssertTracesClientSyncServerSync(port, "data", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithStructParallel()
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
                  this.syncFramedClientWithStruct(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientSyncHsHaServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSync(port, "data", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithStruct() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.syncFramedClientWithStruct(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "data", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithStructMuti() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(port);
    }

    this.waitAndAssertTracesClientSyncServerAsync(port, "data", 5);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithStructParallel()
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
                  this.syncFramedClientWithStruct(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncNonblockingServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync(port, "data", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithStruct() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.syncFramedClientWithStruct(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "data", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithStructMuti() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(port);
    }

    this.waitAndAssertTracesClientSyncServerAsync(port, "data", 5);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithStructParallel()
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
                  this.syncFramedClientWithStruct(port);
                } catch (TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "syncFramedClientAsyncHsHaServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsync(port, "data", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithStruct() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientWithStruct(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "data", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithStructMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(port);
    }

    this.waitAndAssertTracesClientAsyncServerSync(port, "data", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithStructParallel()
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
                  this.nonBlockClientWithStruct(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncNonblockingServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync(port, "data", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithStruct() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientWithStruct(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "data", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithStructMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(port);
    }

    this.waitAndAssertTracesClientAsyncServerSync(port, "data", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithStructParallel()
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
                  this.nonBlockClientWithStruct(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientSyncHsHaServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSync(port, "data", threadCount);
  }

  @Test
  public void syncClientAsyncNonblockingServerWithStructError() {
    Exception error = null;
    int port = super.getPort();
    try {
      this.startAsyncNonblockingServer(port);
      this.syncClientWithStruct(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer(port, "data", 1);
  }

  @Test
  public void syncClientSyncNonblockingServerWithStructError() {
    Exception error = null;
    int port = super.getPort();
    try {
      this.startSyncNonblockingServer(port);
      this.syncClientWithStruct(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer(port, "data", 1);
  }

  @Test
  public void syncClientAsyncHsHaServerWithStructError() {
    Exception error = null;
    int port = super.getPort();
    try {
      this.startAsyncHsHaServer(port);
      this.syncClientWithStruct(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer(port, "data", 1);
  }

  @Test
  public void syncClientSyncHsHaServerWithStructError() {
    Exception error = null;
    int port = super.getPort();
    try {
      this.startSyncHsHaServer(port);
      this.syncClientWithStruct(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer(port, "data", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithStruct() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.nonBlockClientWithStruct(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "data", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithStructMuti() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync(port, "data", 5);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithStructParallel()
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
                  this.nonBlockClientWithStruct(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncNonblockingServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "data", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithStruct() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.nonBlockClientWithStruct(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "data", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithStructMuti() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync(port, "data", 5);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithStructParallel()
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
                  this.nonBlockClientWithStruct(port);
                } catch (IOException | TException e) {
                  count.incrementAndGet();
                  Assertions.fail(
                      "nonBlockClientAsyncHsHaServerWithStructParallel field: "
                          + e.getCause().getMessage());
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }

    latch.await(10L, TimeUnit.SECONDS);
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "data", threadCount);
  }

  public void syncClientWithStruct(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      User user = new User("Bob", "1", 20);
      Account account = new Account("US", "123456");
      UserAccount response = this.testing().runWithSpan("parent", () -> client.data(user, account));
      Assertions.assertThat(response.user).isEqualTo(user);
      Assertions.assertThat(response.account).isEqualTo(account);
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncClientMultiWithStruct(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);

      User user = new User("Bob", "1", 20);
      Account account = new Account("US", "123456");
      UserAccount response = this.testing().runWithSpan("parent", () -> client.data(user, account));
      Assertions.assertThat(response.user).isEqualTo(user);
      Assertions.assertThat(response.account).isEqualTo(account);
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void nonBlockClientWithStruct(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    User user = new User("Bob", "1", 20);
    Account account = new Account("US", "123456");
    AsyncMethodCallback<ThriftService.AsyncClient.data_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.data_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.data_call response) {
            try {
              UserAccount result = response.getResult();
              Assertions.assertThat(result.user).isEqualTo(user);
              Assertions.assertThat(result.account).isEqualTo(account);
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
    this.testing().runWithSpan("parent", () -> asyClient.data(user, account, callback));
  }

  public void syncFramedClientWithStruct(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      User user = new User("Bob", "1", 20);
      Account account = new Account("US", "123456");
      UserAccount response = this.testing().runWithSpan("parent", () -> client.data(user, account));
      Assertions.assertThat(response.user).isEqualTo(user);
      Assertions.assertThat(response.account).isEqualTo(account);
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncFramedClientMultiWithStruct(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      TMultiplexedProtocol multiplexedProtocol =
          new TMultiplexedProtocol(protocol, "syncHelloWorld");
      ThriftService.Client client = new ThriftService.Client(multiplexedProtocol);
      User user = new User("Bob", "1", 20);
      Account account = new Account("US", "123456");
      UserAccount response = this.testing().runWithSpan("parent", () -> client.data(user, account));
      Assertions.assertThat(response.user).isEqualTo(user);
      Assertions.assertThat(response.account).isEqualTo(account);
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncNonblockingSaslClientWithStruct(int port) throws TException, IOException {
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
    User user = new User("Bob", "1", 20);
    Account account = new Account("US", "123456");
    UserAccount response = this.testing().runWithSpan("parent", () -> client.data(user, account));
    Assertions.assertThat(response.user).isEqualTo(user);
    Assertions.assertThat(response.account).isEqualTo(account);
  }
}

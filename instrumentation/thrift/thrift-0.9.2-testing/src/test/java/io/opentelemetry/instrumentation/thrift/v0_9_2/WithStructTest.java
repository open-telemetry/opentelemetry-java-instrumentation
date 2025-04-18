/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_9_2;

import com.google.common.base.VerifyException;
import io.opentelemetry.instrumentation.thrift.v0_9_2.thrift.Account;
import io.opentelemetry.instrumentation.thrift.v0_9_2.thrift.ThriftService;
import io.opentelemetry.instrumentation.thrift.v0_9_2.thrift.User;
import io.opentelemetry.instrumentation.thrift.v0_9_2.thrift.UserAccount;
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

public class WithStructTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerWithStruct() throws TException {
    this.startSyncSimpleServer(this.port);
    this.syncClientWithStruct(this.port);
    this.waitAndAssertTracesClientSyncServerSync("data", 1);
  }

  @Test
  public void syncClientSyncSimpleServerWithStructMuti() throws TException {
    this.startSyncSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithStruct(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSync("data", 5);
  }

  @Test
  public void syncClientSyncSimpleServerWithStructParallel()
      throws TException, InterruptedException {
    this.startSyncSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientSyncServerSync("data", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithStruct() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientWithStruct(port);
    this.waitAndAssertTracesClientSyncServerSync("data", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithStructMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithStruct(port);
    }
    this.waitAndAssertTracesClientSyncServerSync("data", 5);
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
    this.waitAndAssertTracesClientSyncServerSync("data", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithStruct() throws TException {
    this.startMultiSimpleServer(this.port);
    this.syncClientMultiWithStruct(this.port);
    this.waitAndAssertTracesClientSyncServerSync("data", "syncHelloWorld:data", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithStructMuti() throws TException {
    this.startMultiSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiWithStruct(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSync("data", "syncHelloWorld:data", 5);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithStructParallel()
      throws TException, InterruptedException {
    this.startMultiSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientMultiWithStruct(this.port);
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
    this.waitAndAssertTracesClientSyncServerSync("data", "syncHelloWorld:data", threadCount);
  }

  @Test
  public void syncClientSyncThreadedSelectorServerWithStructError() {
    Exception error = null;
    try {
      this.startSyncThreadedSelectorServer(this.port);
      this.syncClientWithStruct(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("data", 1);
  }

  @Test
  public void syncClientAsyncThreadedSelectorServerWithStructError() {
    Exception error = null;
    try {
      this.startAsyncThreadedSelectorServer(this.port);
      this.syncClientWithStruct(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("data", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithStruct() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.syncFramedClientWithStruct(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("data", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithStructMuti() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("data", 5);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithStructParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientSyncServerAsync("data", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithStruct() throws TException {
    this.startMultiThreadedSelectorServer(this.port);
    this.syncFramedClientMultiWithStruct(this.port);
    this.waitAndAssertTracesClientSyncServerSync("data", "syncHelloWorld:data", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithStructMuti() throws TException {
    this.startMultiThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiWithStruct(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("data", "syncHelloWorld:data", 5);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithStructParallel()
      throws TException, InterruptedException {
    this.startMultiThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientMultiWithStruct(this.port);
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
    this.waitAndAssertTracesClientSyncServerSync("data", "syncHelloWorld:data", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithStruct() throws TException {
    this.startSyncThreadedSelectorServer(this.port);
    this.syncFramedClientWithStruct(this.port);
    this.waitAndAssertTracesClientSyncServerSync("data", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithStructMuti() throws TException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("data", 5);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithStructParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientSyncServerSync("data", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithStruct() throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.nonBlockClientWithStruct(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("data", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithStructMuti()
      throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("data", 5);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithStructParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientAsyncServerAsync("data", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithStruct() throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);
    this.nonBlockClientWithStruct(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("data", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithStructMuti()
      throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("data", 5);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithStructParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientAsyncServerSync("data", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithStruct() throws TException {
    this.startSyncNonblockingServer(this.port);
    this.syncFramedClientWithStruct(this.port);
    this.waitAndAssertTracesClientSyncServerSync("data", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithStructMuti() throws TException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("data", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithStructParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientSyncServerSync("data", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithStruct() throws TException {
    this.startSyncHsHaServer(this.port);
    this.syncFramedClientWithStruct(this.port);
    this.waitAndAssertTracesClientSyncServerSync("data", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithStructMuti() throws TException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSync("data", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithStructParallel()
      throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientSyncServerSync("data", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithStruct() throws TException {
    this.startAsyncNonblockingServer(this.port);
    this.syncFramedClientWithStruct(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("data", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithStructMuti() throws TException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("data", 5);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithStructParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientSyncServerAsync("data", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithStruct() throws TException {
    this.startAsyncHsHaServer(this.port);
    this.syncFramedClientWithStruct(this.port);
    this.waitAndAssertTracesClientSyncServerAsync("data", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithStructMuti() throws TException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsync("data", 5);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithStructParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientSyncServerAsync("data", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithStruct() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);
    this.nonBlockClientWithStruct(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("data", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithStructMuti() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("data", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithStructParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientAsyncServerSync("data", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithStruct() throws TException, IOException {
    this.startSyncHsHaServer(this.port);
    this.nonBlockClientWithStruct(this.port);
    this.waitAndAssertTracesClientAsyncServerSync("data", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithStructMuti() throws TException, IOException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("data", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithStructParallel()
      throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientAsyncServerSync("data", threadCount);
  }

  @Test
  public void syncClientAsyncNonblockingServerWithStructError() {
    Exception error = null;
    try {
      this.startAsyncNonblockingServer(this.port);
      this.syncClientWithStruct(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("data", 1);
  }

  @Test
  public void syncClientSyncNonblockingServerWithStructError() {
    Exception error = null;
    try {
      this.startSyncNonblockingServer(this.port);
      this.syncClientWithStruct(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("data", 1);
  }

  @Test
  public void syncClientAsyncHsHaServerWithStructError() {
    Exception error = null;
    try {
      this.startAsyncHsHaServer(this.port);
      this.syncClientWithStruct(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("data", 1);
  }

  @Test
  public void syncClientSyncHsHaServerWithStructError() {
    Exception error = null;
    try {
      this.startSyncHsHaServer(this.port);
      this.syncClientWithStruct(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("data", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithStruct() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);
    this.nonBlockClientWithStruct(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("data", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithStructMuti() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("data", 5);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithStructParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientAsyncServerAsync("data", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithStruct() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);
    this.nonBlockClientWithStruct(this.port);
    this.waitAndAssertTracesClientAsyncServerAsync("data", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithStructMuti() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithStruct(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsync("data", 5);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithStructParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithStruct(this.port);
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
    this.waitAndAssertTracesClientAsyncServerAsync("data", threadCount);
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

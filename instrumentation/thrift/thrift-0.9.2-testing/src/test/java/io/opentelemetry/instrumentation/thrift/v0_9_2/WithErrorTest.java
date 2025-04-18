/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_9_2;

import com.google.common.base.VerifyException;
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

public class WithErrorTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerWithError() throws TException {
    this.startSyncSimpleServer(this.port);
    this.syncClientWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 1);
  }

  @Test
  public void syncClientSyncSimpleServerWithErrorMuti() throws TException {
    this.startSyncSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithError(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 5);
  }

  @Test
  public void syncClientSyncSimpleServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithError() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithError(port);
    }
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 5);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithError() throws TException {
    this.startMultiSimpleServer(this.port);
    this.syncClientMultiWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        "withError", "syncHelloWorld:withError", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithErrorMuti() throws TException {
    this.startMultiSimpleServer(this.port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientMultiWithError(this.port);
    }
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        "withError", "syncHelloWorld:withError", 5);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startMultiSimpleServer(this.port);
    AtomicInteger count = new AtomicInteger(0);
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncClientMultiWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        "withError", "syncHelloWorld:withError", threadCount);
  }

  @Test
  public void syncClientSyncThreadedSelectorServerWithErrorError() {
    Exception error = null;
    try {
      this.startSyncThreadedSelectorServer(this.port);
      this.syncClientWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServer("withError", 1);
  }

  @Test
  public void syncClientAsyncThreadedSelectorServerWithErrorError() {
    Exception error = null;
    try {
      this.startAsyncThreadedSelectorServer(this.port);
      this.syncClientWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServer("withError", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithError() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.syncFramedClientWithError(this.port);
    this.waitAndAssertTracesClientSyncServerAsyncWithError("withError", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithErrorMuti() throws TException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncWithError("withError", 5);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncWithError("withError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithError() throws TException {
    this.startMultiThreadedSelectorServer(this.port);
    this.syncFramedClientMultiWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        "withError", "syncHelloWorld:withError", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithErrorMuti() throws TException {
    this.startMultiThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientMultiWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSyncWithError(
        "withError", "syncHelloWorld:withError", 5);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startMultiThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientMultiWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        "withError", "syncHelloWorld:withError", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithError() throws TException {
    this.startSyncThreadedSelectorServer(this.port);
    this.syncFramedClientWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithErrorMuti() throws TException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 5);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithError() throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);
    this.nonBlockClientWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError("withError", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithErrorMuti()
      throws TException, IOException {
    this.startAsyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncWithError("withError", 5);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError("withError", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithError() throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);
    this.nonBlockClientWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithErrorMuti()
      throws TException, IOException {
    this.startSyncThreadedSelectorServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", 5);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncThreadedSelectorServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithError() throws TException {
    this.startSyncNonblockingServer(this.port);
    this.syncFramedClientWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithErrorMuti() throws TException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithError() throws TException {
    this.startSyncHsHaServer(this.port);
    this.syncFramedClientWithError(this.port);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithErrorMuti() throws TException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithError() throws TException {
    this.startAsyncNonblockingServer(this.port);
    this.syncFramedClientWithError(this.port);
    this.waitAndAssertTracesClientSyncServerAsyncWithError("withError", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithErrorMuti() throws TException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncWithError("withError", 5);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncWithError("withError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithError() throws TException {
    this.startAsyncHsHaServer(this.port);
    this.syncFramedClientWithError(this.port);
    this.waitAndAssertTracesClientSyncServerAsyncWithError("withError", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithErrorMuti() throws TException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(this.port);
    }

    this.waitAndAssertTracesClientSyncServerAsyncWithError("withError", 5);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.syncFramedClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientSyncServerAsyncWithError("withError", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithError() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);
    this.nonBlockClientWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithErrorMuti() throws TException, IOException {
    this.startSyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithError() throws TException, IOException {
    this.startSyncHsHaServer(this.port);
    this.nonBlockClientWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithErrorMuti() throws TException, IOException {
    this.startSyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startSyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", threadCount);
  }

  @Test
  public void syncClientAsyncNonblockingServerWithErrorError() {
    Exception error = null;
    try {
      this.startAsyncNonblockingServer(this.port);
      this.syncClientWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServer("withError", 1);
  }

  @Test
  public void syncClientSyncNonblockingServerWithErrorError() {
    Exception error = null;
    try {
      this.startSyncNonblockingServer(this.port);
      this.syncClientWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServer("withError", 1);
  }

  @Test
  public void syncClientAsyncHsHaServerWithErrorError() {
    Exception error = null;
    try {
      this.startAsyncHsHaServer(this.port);
      this.syncClientWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServer("withError", 1);
  }

  @Test
  public void syncClientSyncHsHaServerWithErrorError() {
    Exception error = null;
    try {
      this.startSyncHsHaServer(this.port);
      this.syncClientWithError(this.port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServer("withError", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithError() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);
    this.nonBlockClientWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError("withError", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithErrorMuti() throws TException, IOException {
    this.startAsyncNonblockingServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncWithError("withError", 5);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncNonblockingServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError("withError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithError() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);
    this.nonBlockClientWithError(this.port);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError("withError", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithErrorMuti() throws TException, IOException {
    this.startAsyncHsHaServer(this.port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(this.port);
    }

    this.waitAndAssertTracesClientAsyncServerAsyncWithError("withError", 5);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithErrorParallel()
      throws TException, InterruptedException {
    this.startAsyncHsHaServer(this.port);
    AtomicInteger count = new AtomicInteger();
    int threadCount = 5;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; ++i) {
      new Thread(
              () -> {
                try {
                  this.nonBlockClientWithError(this.port);
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
    Assertions.assertThat(count.get()).isEqualTo(0);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError("withError", threadCount);
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
      Assertions.assertThat(error).isNotNull();
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
      Assertions.assertThat(error).isNotNull();
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
      Assertions.assertThat(error).isNotNull();
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
      Assertions.assertThat(error).isNotNull();
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void syncNonblockingSaslClientWithError(int port) throws TException, IOException {
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
      this.testing().runWithSpan("parent", () -> client.withError());
    } catch (Exception e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
  }
}

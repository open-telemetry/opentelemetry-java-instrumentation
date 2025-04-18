/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0;

import io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0.thrift.ThriftService;
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
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 1);
  }

  @Test
  public void syncClientSyncSimpleServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithError(port);
    }
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 5);
  }

  @Test
  public void syncClientSyncSimpleServerWithErrorParallel()
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
  public void syncFramedClientSyncNonblockingServerWithError() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithErrorParallel()
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
                  this.syncFramedClientWithError(port);
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
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithErrorMuti() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithError(port);
    }

    this.waitAndAssertTracesClientSyncServerSyncWithError("withError", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithErrorParallel()
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
                  this.syncFramedClientWithError(port);
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
  public void nonBlockClientSyncNonblockingServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithErrorMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithErrorParallel()
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
                  this.nonBlockClientWithError(port);
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
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithErrorMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithError(port);
    }

    this.waitAndAssertTracesClientAsyncServerSyncWithError("withError", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithErrorParallel()
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
                  this.nonBlockClientWithError(port);
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
  public void syncClientSyncNonblockingServerWithErrorError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startSyncNonblockingServer(port);
      this.syncClientWithError(port);
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
      int port = super.getPort();
      this.startSyncHsHaServer(port);
      this.syncClientWithError(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNull();
    this.waitAndAssertTracesClientSyncNoServer("withError", 1);
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
            Exception error = null;
            try {
              String result = s.getResult();
              Assertions.assertThat(result).isEqualTo("Hello USs' Bob");
            } catch (TException e) {
              error = e;
            }
            Assertions.assertThat(error).isNull();
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

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_7_0;

import com.google.common.base.VerifyException;
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

public class WithoutArgsTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerWithoutArgs() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientWithoutArgs(port);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 1);
  }

  @Test
  public void syncClientSyncSimpleServerWithoutArgsMuti() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    for (int i = 0; i < 5; ++i) {
      this.syncClientWithoutArgs(port);
    }
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 5);
  }

  @Test
  public void syncClientSyncSimpleServerWithoutArgsParallel()
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
  public void syncFramedClientSyncNonblockingServerWithoutArgs() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientWithoutArgs(port);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithoutArgsMuti() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithoutArgs(port);
    }

    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 5);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithoutArgsParallel()
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
                  this.syncFramedClientWithoutArgs(port);
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
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientWithoutArgs(port);
    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithoutArgsMuti() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.syncFramedClientWithoutArgs(port);
    }

    this.waitAndAssertTracesClientSyncServerSync("withoutArgs", 5);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithoutArgsParallel()
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
                  this.syncFramedClientWithoutArgs(port);
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
  public void nonBlockClientSyncNonblockingServerWithoutArgs() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientWithoutArgs(port);
    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithoutArgsMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithoutArgs(port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", 5);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithoutArgsParallel()
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
                  this.nonBlockClientWithoutArgs(port);
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
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientWithoutArgs(port);
    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithoutArgsMuti() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);

    for (int i = 0; i < 5; ++i) {
      this.nonBlockClientWithoutArgs(port);
    }

    this.waitAndAssertTracesClientAsyncServerSync("withoutArgs", 5);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithoutArgsParallel()
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
                  this.nonBlockClientWithoutArgs(port);
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
  public void syncClientSyncNonblockingServerWithoutArgsError() {
    Exception error = null;
    try {
      int port = super.getPort();
      this.startSyncNonblockingServer(port);
      this.syncClientWithoutArgs(port);
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
      int port = super.getPort();
      this.startSyncHsHaServer(port);
      this.syncClientWithoutArgs(port);
    } catch (TException e) {
      error = e;
    }
    Assertions.assertThat(error).isNotNull();
    this.waitAndAssertTracesClientSyncNoServer("withoutArgs", 1);
  }

  public void syncClientWithoutArgs(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      String response = this.testing().runWithSpan("parent", () -> client.withoutArgs());
      Assertions.assertThat(response).isEqualTo("no args");
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }

  public void nonBlockClientWithoutArgs(int port) throws TException, IOException {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    ThriftService.AsyncClient.Factory factory =
        new ThriftService.AsyncClient.Factory(clientManager, protocolFactory);
    ThriftService.AsyncClient asyClient = factory.getAsyncClient(transport);
    AsyncMethodCallback<ThriftService.AsyncClient.withoutArgs_call> callback =
        new AsyncMethodCallback<ThriftService.AsyncClient.withoutArgs_call>() {
          @Override
          public void onComplete(ThriftService.AsyncClient.withoutArgs_call s) {
            try {
              String result = s.getResult();
              Assertions.assertThat(result).isEqualTo("no args");
            } catch (TException e) {
              throw new VerifyException(e);
            }
          }

          @Override
          public void onError(Exception e) {
            throw new VerifyException("asyncClientWithoutArgs test failed", e);
          }
        };
    this.testing().runWithSpan("parent", () -> asyClient.withoutArgs(callback));
  }

  public void syncFramedClientWithoutArgs(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      TFramedTransport framedTransport = new TFramedTransport(transport);
      framedTransport.open();
      TProtocol protocol = new TBinaryProtocol(framedTransport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      String response = this.testing().runWithSpan("parent", () -> client.withoutArgs());
      Assertions.assertThat(response).isEqualTo("no args");
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
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

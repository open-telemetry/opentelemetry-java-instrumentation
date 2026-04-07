/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.thrift.ThriftService;
import java.io.IOException;
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
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.jupiter.api.Test;

public class WithErrorTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerWithError() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void syncClientSyncSimpleServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithError() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithError() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    this.syncClientMultiWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        port, "withError", "syncHelloWorld:withError", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientMultiWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        port, "withError", "syncHelloWorld:withError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithError() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientWithError(port));
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithError() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    this.syncFramedClientMultiWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        port, "withError", "syncHelloWorld:withError", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientMultiWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncWithError(
        port, "withError", "syncHelloWorld:withError", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithError() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientWithError(port));
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientWithError(port));
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithError() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithError() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientWithError(port));
    this.waitAndAssertTracesClientSyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithError() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientWithError(port));
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithError() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.syncFramedClientWithError(port);
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientWithError(port));
    this.waitAndAssertTracesClientSyncServerAsyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientWithError(port));
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientWithError(port));
    this.waitAndAssertTracesClientAsyncServerSyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientWithError(port));
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithError() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.nonBlockClientWithError(port);
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerWithErrorParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientWithError(port));
    this.waitAndAssertTracesClientAsyncServerAsyncWithError(port, "withError", threadCount);
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
      assertThat(error).isNotNull();
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
      assertThat(error).isNotNull();
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
    AsyncMethodCallback<String> callback =
        new AsyncMethodCallback<String>() {
          @Override
          public void onComplete(String result) {
            assertThat(result).isEqualTo("Hello USs' Bob");
          }

          @Override
          public void onError(Exception e) {
            assertThat(e.getMessage()).isEqualTo("Read call frame size failed");
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
      assertThat(error).isNotNull();
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
      assertThat(error).isNotNull();
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }
}

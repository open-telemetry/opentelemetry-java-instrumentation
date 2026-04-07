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

public class SayHelloTest extends ThriftBaseTest {

  @Test
  public void syncClientSyncSimpleServerSayHello() throws TException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    this.syncClientSayHello(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", 1);
  }

  @Test
  public void syncClientSyncSimpleServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncSimpleServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientSayHello(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", threadCount);
  }

  @Test
  public void syncClientSyncThreadPoolServerSayHello() throws TException, InterruptedException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    this.syncClientSayHello(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", 1);
  }

  @Test
  public void syncClientSyncThreadPoolServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadPoolServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientSayHello(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", threadCount);
  }

  @Test
  public void syncClientMutiSyncSimpleServerSayHello() throws TException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    this.syncClientMultiSayHello(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", "syncHelloWorld:sayHello", 1);
  }

  @Test
  public void syncClientMutiSyncSimpleServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startMultiSimpleServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncClientMultiSayHello(port));
    this.waitAndAssertTracesClientSyncServerSync(
        port, "sayHello", "syncHelloWorld:sayHello", threadCount);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerSayHello() throws TException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.syncFramedClientSayHello(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "sayHello", 1);
  }

  @Test
  public void syncFramedClientAsyncThreadedSelectorServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientSayHello(port));
    this.waitAndAssertTracesClientSyncServerAsync(port, "sayHello", threadCount);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerSayHello() throws TException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    this.syncFramedClientMultiSayHello(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", "syncHelloWorld:sayHello", 1);
  }

  @Test
  public void syncFramedClientAsyncMutiThreadedSelectorServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startMultiThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientMultiSayHello(port));
    this.waitAndAssertTracesClientSyncServerSync(
        port, "sayHello", "syncHelloWorld:sayHello", threadCount);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerSayHello() throws TException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.syncFramedClientSayHello(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", 1);
  }

  @Test
  public void syncFramedClientSyncThreadedSelectorServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientSayHello(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", threadCount);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    this.nonBlockClientSayHello(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "sayHello", 1);
  }

  @Test
  public void nonBlockClientAsyncThreadedSelectorServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientSayHello(port));
    this.waitAndAssertTracesClientAsyncServerAsync(port, "sayHello", threadCount);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    this.nonBlockClientSayHello(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "sayHello", 1);
  }

  @Test
  public void nonBlockClientSyncThreadedSelectorServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncThreadedSelectorServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientSayHello(port));
    this.waitAndAssertTracesClientAsyncServerSync(port, "sayHello", threadCount);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerSayHello() throws TException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.syncFramedClientSayHello(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", 1);
  }

  @Test
  public void syncFramedClientSyncNonblockingServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientSayHello(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", threadCount);
  }

  @Test
  public void syncFramedClientSyncHsHaServerSayHello() throws TException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.syncFramedClientSayHello(port);
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", 1);
  }

  @Test
  public void syncFramedClientSyncHsHaServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientSayHello(port));
    this.waitAndAssertTracesClientSyncServerSync(port, "sayHello", threadCount);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerSayHello() throws TException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.syncFramedClientSayHello(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "sayHello", 1);
  }

  @Test
  public void syncFramedClientAsyncNonblockingServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientSayHello(port));
    this.waitAndAssertTracesClientSyncServerAsync(port, "sayHello", threadCount);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerSayHello() throws TException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.syncFramedClientSayHello(port);
    this.waitAndAssertTracesClientSyncServerAsync(port, "sayHello", 1);
  }

  @Test
  public void syncFramedClientAsyncHsHaServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.syncFramedClientSayHello(port));
    this.waitAndAssertTracesClientSyncServerAsync(port, "sayHello", threadCount);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    this.nonBlockClientSayHello(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "sayHello", 1);
  }

  @Test
  public void nonBlockClientSyncNonblockingServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientSayHello(port));
    this.waitAndAssertTracesClientAsyncServerSync(port, "sayHello", threadCount);
  }

  @Test
  public void nonBlockClientSyncHsHaServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    this.nonBlockClientSayHello(port);
    this.waitAndAssertTracesClientAsyncServerSync(port, "sayHello", 1);
  }

  @Test
  public void nonBlockClientSyncHsHaServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startSyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientSayHello(port));
    this.waitAndAssertTracesClientAsyncServerSync(port, "sayHello", threadCount);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    this.nonBlockClientSayHello(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "sayHello", 1);
  }

  @Test
  public void nonBlockClientAsyncNonblockingServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncNonblockingServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientSayHello(port));
    this.waitAndAssertTracesClientAsyncServerAsync(port, "sayHello", threadCount);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerSayHello() throws TException, IOException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    this.nonBlockClientSayHello(port);
    this.waitAndAssertTracesClientAsyncServerAsync(port, "sayHello", 1);
  }

  @Test
  public void nonBlockClientAsyncHsHaServerSayHelloParallel()
      throws InterruptedException, TTransportException {
    int port = super.getPort();
    this.startAsyncHsHaServer(port);
    int threadCount = 5;
    runParallel(threadCount, () -> this.nonBlockClientSayHello(port));
    this.waitAndAssertTracesClientAsyncServerAsync(port, "sayHello", threadCount);
  }

  public void syncClientSayHello(int port) throws TException {
    TTransport transport = null;
    try {
      transport = new TSocket("localhost", port);
      transport.open();
      TProtocol protocol = new TBinaryProtocol(transport);
      ThriftService.Client client = new ThriftService.Client(protocol);
      String response = this.testing().runWithSpan("parent", () -> client.sayHello("US", "Bob"));
      assertThat(response).isEqualTo("Hello USs' Bob");
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
      assertThat(response).isEqualTo("Hello USs' Bob");
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
      assertThat(response).isEqualTo("Hello USs' Bob");
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
      assertThat(response).isEqualTo("Hello USs' Bob");
    } finally {
      if (transport != null) {
        transport.close();
      }
    }
  }
}

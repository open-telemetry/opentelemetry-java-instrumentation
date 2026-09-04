/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

class TestRedisCluster implements AutoCloseable {

  private static final String NODE_ID = "0000000000000000000000000000000000000001";

  private final ServerSocket serverSocket;
  private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
  private final AtomicReference<Throwable> failure = new AtomicReference<>();
  private final Thread acceptThread;
  private volatile boolean closed;

  TestRedisCluster() {
    try {
      serverSocket = new ServerSocket(0, 50, InetAddress.getAllByName("127.0.0.1")[0]);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    acceptThread = new Thread(this::acceptConnections, "test-vertx-redis-cluster-accept");
    acceptThread.setDaemon(true);
    acceptThread.start();
  }

  String getHost() {
    return serverSocket.getInetAddress().getHostAddress();
  }

  int getPort() {
    return serverSocket.getLocalPort();
  }

  private void acceptConnections() {
    while (!closed) {
      try {
        Socket socket = serverSocket.accept();
        connections.add(socket);
        Thread thread =
            new Thread(() -> handleConnection(socket), "test-vertx-redis-cluster-connection");
        thread.setDaemon(true);
        thread.start();
      } catch (IOException e) {
        if (!closed) {
          failure.compareAndSet(null, e);
        }
      }
    }
  }

  private void handleConnection(Socket socket) {
    try (DataInputStream input = new DataInputStream(socket.getInputStream())) {
      OutputStream output = socket.getOutputStream();
      while (true) {
        List<String> command = readCommand(input);
        if (command.isEmpty()) {
          break;
        }
        writeResponse(command, output);
      }
    } catch (IOException e) {
      if (!closed) {
        failure.compareAndSet(null, e);
      }
    } finally {
      connections.remove(socket);
    }
  }

  private void writeResponse(List<String> command, OutputStream output) throws IOException {
    String name = command.get(0).toUpperCase(Locale.ROOT);
    if ("HELLO".equals(name)) {
      write(output, "%1\r\n+proto\r\n:3\r\n");
    } else if ("CLUSTER".equals(name) && command.size() > 1) {
      writeClusterResponse(command.get(1), output);
    } else if ("SET".equals(name) || "CLIENT".equals(name)) {
      write(output, "+OK\r\n");
    } else if ("COMMAND".equals(name)) {
      write(output, "*0\r\n");
    } else if ("PING".equals(name)) {
      write(output, "+PONG\r\n");
    } else {
      AssertionError error = new AssertionError("Unexpected Redis command: " + command);
      failure.compareAndSet(null, error);
      write(output, "-ERR unsupported command\r\n");
    }
  }

  private void writeClusterResponse(String subcommand, OutputStream output) throws IOException {
    String name = subcommand.toUpperCase(Locale.ROOT);
    if ("SLOTS".equals(name)) {
      String host = getHost();
      write(
          output,
          "*1\r\n"
              + "*3\r\n"
              + ":0\r\n"
              + ":16383\r\n"
              + "*3\r\n"
              + "$"
              + host.getBytes(UTF_8).length
              + "\r\n"
              + host
              + "\r\n"
              + ":"
              + getPort()
              + "\r\n"
              + "$"
              + NODE_ID.length()
              + "\r\n"
              + NODE_ID
              + "\r\n");
    } else if ("NODES".equals(name)) {
      String nodes =
          NODE_ID
              + " "
              + getHost()
              + ":"
              + getPort()
              + " myself,master - 0 0 1 connected 0-16383\n";
      write(output, "$" + nodes.getBytes(UTF_8).length + "\r\n" + nodes + "\r\n");
    } else if ("INFO".equals(name)) {
      String info = "cluster_state:ok\r\n";
      write(output, "$" + info.length() + "\r\n" + info + "\r\n");
    } else {
      AssertionError error = new AssertionError("Unexpected CLUSTER subcommand: " + subcommand);
      failure.compareAndSet(null, error);
      write(output, "-ERR unsupported CLUSTER subcommand\r\n");
    }
  }

  private static List<String> readCommand(DataInputStream input) throws IOException {
    int first = input.read();
    if (first == -1) {
      return emptyList();
    }
    if (first != '*') {
      throw new IOException("Expected RESP array");
    }
    int argumentCount = Integer.parseInt(readLine(input));
    List<String> command = new ArrayList<>(argumentCount);
    for (int i = 0; i < argumentCount; i++) {
      if (input.read() != '$') {
        throw new IOException("Expected RESP bulk string");
      }
      int length = Integer.parseInt(readLine(input));
      byte[] value = new byte[length];
      input.readFully(value);
      if (input.read() != '\r' || input.read() != '\n') {
        throw new IOException("Expected RESP line ending");
      }
      command.add(new String(value, UTF_8));
    }
    return command;
  }

  private static String readLine(DataInputStream input) throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    int value;
    while ((value = input.read()) != '\r') {
      if (value == -1) {
        throw new IOException("Unexpected end of RESP input");
      }
      line.write(value);
    }
    if (input.read() != '\n') {
      throw new IOException("Expected RESP line ending");
    }
    return new String(line.toByteArray(), US_ASCII);
  }

  private static void write(OutputStream output, String value) throws IOException {
    output.write(value.getBytes(UTF_8));
    output.flush();
  }

  void assertNoFailure() {
    assertThat(failure.get()).isNull();
  }

  @Override
  public void close() {
    closed = true;
    try {
      serverSocket.close();
      for (Socket connection : new ArrayList<>(connections)) {
        connection.close();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

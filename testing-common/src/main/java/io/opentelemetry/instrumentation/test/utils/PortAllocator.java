/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for finding open ports that test servers can bind. Allocator splits allocation range
 * to chunks and binds to the first port in chunk to claim it. If first port of chunk is already in
 * use allocator assumes that some other process has already claimed that chunk and moves to next
 * chunk. This should let us as run tests in parallel without them interfering with each other.
 */
class PortAllocator {

  static final int CHUNK_SIZE = 100;
  static final int RANGE_MIN = 11000;
  // end of allocator port range, should be below ephemeral port range
  static final int RANGE_MAX = 32768;

  private final PortBinder portBinder;
  private final List<Closeable> sockets = new ArrayList<>();
  // next candidate port
  private int next = RANGE_MIN;
  private int nextChunkStart = RANGE_MIN;

  PortAllocator() {
    this(PortBinder.INSTANCE);
  }

  PortAllocator(PortBinder portBinder) {
    this.portBinder = portBinder;

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  for (Closeable socket : sockets) {
                    try {
                      socket.close();
                    } catch (IOException ignored) {
                      // Ignore
                    }
                  }
                }));
  }

  /** Find open port. */
  int getPort() {
    return getPorts(1);
  }

  /** Find consecutive range of open ports, returning the first one in the range. */
  synchronized int getPorts(int count) {
    // as we bind to first port in each chunk the max amount of
    // consecutive ports that we can find is CHUNK_SIZE - 1
    if (count < 1 || count >= CHUNK_SIZE) {
      throw new IllegalStateException("Invalid count " + count);
    }
    while (next + count - 1 <= RANGE_MAX) {
      // if current chunk doesn't have enough ports move to next chunk
      if (next + count - 1 >= nextChunkStart) {
        reserveNextChunk();
      }
      // find requested amount of consecutive ports
      while (next + count - 1 < nextChunkStart && next + count - 1 <= RANGE_MAX) {
        // result is the lowest port in consecutive range
        int result = next;
        for (int i = 0; i < count; i++) {
          int port = next;
          next++;
          if (!portBinder.canBind(port)) {
            // someone has allocated a port in our port range, ignore it and try with
            // the next port
            break;
          } else if (i == count - 1) {
            return result;
          }
        }
      }
    }
    // give up when port range is exhausted
    throw new IllegalStateException("Failed to find suitable port");
  }

  private void reserveNextChunk() {
    while (nextChunkStart < RANGE_MAX) {
      // reserve a chunk, if binding to first port of chunk fails
      // move to next chunk
      Closeable serverSocket = portBinder.bind(nextChunkStart);
      if (serverSocket != null) {
        sockets.add(serverSocket);
        next = nextChunkStart + 1;
        nextChunkStart += CHUNK_SIZE;
        return;
      }
      nextChunkStart += CHUNK_SIZE;
    }
    // give up when port range is exhausted
    throw new IllegalStateException("Failed to reserve suitable port range");
  }

  static class PortBinder {
    static final PortBinder INSTANCE = new PortBinder();

    Closeable bind(int port) {
      try {
        return new ServerSocket(port);
      } catch (IOException exception) {
        return null;
      }
    }

    boolean canBind(int port) {
      try {
        ServerSocket socket = new ServerSocket(port);
        socket.close();
        return true;
      } catch (IOException exception) {
        return false;
      }
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

public final class VertxRedisClientRequest {
  private final String command;
  private final List<byte[]> args;
  private final String connectionInfo;

  public VertxRedisClientRequest(
      String command, List<byte[]> args, @Nullable String connectionInfo) {
    this.command = cleanRedisCommand(command).toUpperCase(Locale.ROOT);
    this.args = args;
    this.connectionInfo = connectionInfo;
  }

  /**
   * Cleans RESP protocol formatting from Redis command names
   * Converts "$3\nSET\n" → "SET", "$4\nHGET\n" → "HGET", etc.
   */
  private static String cleanRedisCommand(String rawCommand) {
    if (rawCommand == null || rawCommand.isEmpty()) {
      return rawCommand;
    }
    
    // Check if it starts with RESP format ($<length>\n)
    if (rawCommand.startsWith("$")) {
      int firstNewline = rawCommand.indexOf('\n');
      if (firstNewline != -1) {
        int secondNewline = rawCommand.indexOf('\n', firstNewline + 1);
        if (secondNewline != -1) {
          // Extract just the command between the newlines
          return rawCommand.substring(firstNewline + 1, secondNewline);
        } else {
          // No second newline, take everything after first newline
          return rawCommand.substring(firstNewline + 1);
        }
      }
    }
    
    // If not RESP format, return as-is
    return rawCommand;
  }

  public String getCommand() {
    return command;
  }

  public List<byte[]> getArgs() {
    return args;
  }

  @Nullable
  public String getUser() {
    return null; // Not available in 3.9 API
  }

  @Nullable
  public Long getDatabaseIndex() {
    // Try to extract database index from connection info if available
    if (connectionInfo != null && connectionInfo.contains("/")) {
      try {
        String[] parts = connectionInfo.split("/");
        if (parts.length > 1) {
          return Long.parseLong(parts[parts.length - 1]);
        }
      } catch (NumberFormatException e) {
        // Ignore parsing errors
      }
    }
    return null;
  }

  @Nullable
  public String getConnectionString() {
    return null;
  }

  @Nullable
  public String getHost() {
    // Try to extract host from connection info
    if (connectionInfo != null) {
      try {
        // Expected format: host:port or host:port/db
        String hostPort = connectionInfo.split("/")[0];
        return hostPort.split(":")[0];
      } catch (RuntimeException e) {
        // Ignore parsing errors
      }
    }
    return null;
  }

  @Nullable
  public Integer getPort() {
    // Try to extract port from connection info
    if (connectionInfo != null) {
      try {
        // Expected format: host:port or host:port/db
        String hostPort = connectionInfo.split("/")[0];
        String[] parts = hostPort.split(":");
        if (parts.length > 1) {
          return Integer.parseInt(parts[1]);
        }
      } catch (RuntimeException e) {
        // Ignore parsing errors
      }
    }
    return null;
  }

  @Nullable
  public String getPeerAddress() {
    return getHost(); // Same as host for 3.9
  }

  @Nullable
  public Integer getPeerPort() {
    return getPort(); // Same as port for 3.9
  }
}

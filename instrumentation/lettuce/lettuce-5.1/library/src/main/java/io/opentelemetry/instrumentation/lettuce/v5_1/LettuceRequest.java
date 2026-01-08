/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter.splitArgs;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Represents a Lettuce Redis command request, containing all information needed for
 * instrumentation.
 */
final class LettuceRequest {

  private final RedisCommandSanitizer sanitizer;
  @Nullable private volatile String command;
  @Nullable private volatile List<String> argsList;
  @Nullable private volatile String argsString;
  @Nullable private volatile InetSocketAddress address;
  @Nullable private volatile Long databaseIndex;

  LettuceRequest(RedisCommandSanitizer sanitizer) {
    this.sanitizer = sanitizer;
  }

  void setCommand(String command) {
    this.command = command;
  }

  @Nullable
  String getCommand() {
    return command;
  }

  void setArgsList(List<String> argsList) {
    this.argsList = argsList;
  }

  void setArgsString(String argsString) {
    this.argsString = argsString;
  }

  void setAddress(InetSocketAddress address) {
    this.address = address;
  }

  @Nullable
  InetSocketAddress getAddress() {
    return address;
  }

  void setDatabaseIndex(long databaseIndex) {
    this.databaseIndex = databaseIndex;
  }

  @Nullable
  Long getDatabaseIndex() {
    return databaseIndex;
  }

  @Nullable
  String getStatement() {
    String cmd = command;
    if (cmd == null) {
      return null;
    }
    List<String> args = argsList;
    if (args == null) {
      args = splitArgs(argsString);
    }
    return sanitizer.sanitize(cmd, args);
  }
}

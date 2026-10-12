/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static java.util.Collections.emptyList;

import com.google.auto.value.AutoValue;
import java.util.List;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;

@AutoValue
public abstract class JedisRequest {

  public static JedisRequest create(Connection connection, Protocol.Command command) {
    return new AutoValue_JedisRequest(connection, command, emptyList());
  }

  public static JedisRequest create(
      Connection connection, Protocol.Command command, List<byte[]> args) {
    return new AutoValue_JedisRequest(connection, command, args);
  }

  public abstract Connection getConnection();

  public abstract Protocol.Command getCommand();

  public abstract List<byte[]> getArgs();
}

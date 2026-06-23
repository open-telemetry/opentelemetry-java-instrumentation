/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import java.util.List;
import java.util.StringJoiner;
import javax.annotation.Nullable;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.commands.ProtocolCommand;

@AutoValue
public abstract class JedisRequest {

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "jedis"));

  public static JedisRequest create(
      Connection connection, ProtocolCommand command, List<byte[]> args) {
    String operationName = operationName(command);
    return new AutoValue_JedisRequest(
        connection, operationName, sanitizer.sanitize(operationName, args), null);
  }

  public static JedisRequest createPipeline(List<JedisRequest> requests) {
    return createBatch(requests, "PIPELINE");
  }

  public static JedisRequest createTransaction(List<JedisRequest> requests) {
    return createBatch(requests, "MULTI");
  }

  private static JedisRequest createBatch(List<JedisRequest> requests, String prefix) {
    JedisRequest first = requests.get(0);
    return new AutoValue_JedisRequest(
        first.getConnection(),
        batchOperationName(requests, prefix),
        pipelineQueryText(requests),
        requests.size() != 1 ? (long) requests.size() : null);
  }

  public abstract Connection getConnection();

  public abstract String getOperationName();

  public abstract String getQueryText();

  @Nullable
  public abstract Long getBatchSize();

  private static String operationName(ProtocolCommand command) {
    if (command instanceof Protocol.Command) {
      return ((Protocol.Command) command).name();
    } else {
      // Protocol.Command is the only implementation in the Jedis lib as of 3.1 but this will save
      // us if that changes
      return new String(command.getRaw(), UTF_8);
    }
  }

  private static String batchOperationName(List<JedisRequest> requests, String prefix) {
    if (requests.size() == 1) {
      return requests.get(0).getOperationName();
    }
    String commonOperationName = requests.get(0).getOperationName();
    for (int i = 1; i < requests.size(); i++) {
      if (!commonOperationName.equals(requests.get(i).getOperationName())) {
        return prefix;
      }
    }
    return prefix + " " + commonOperationName;
  }

  private static String pipelineQueryText(List<JedisRequest> requests) {
    StringJoiner joiner = new StringJoiner(";");
    for (JedisRequest request : requests) {
      joiner.add(request.getQueryText());
    }
    return joiner.toString();
  }
}

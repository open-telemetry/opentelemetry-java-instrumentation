/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.emptyList;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;

@AutoValue
public abstract class JedisRequest {
  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "jedis"));
  private static final int LIMIT = 32 * 1024;

  public static JedisRequest create(Connection connection, Protocol.Command command) {
    return create(connection, command, emptyList());
  }

  public static JedisRequest create(
      Connection connection, Protocol.Command command, List<byte[]> args) {
    String operationName = command.name();
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
    StringBuilder builder = new StringBuilder();
    for (JedisRequest request : requests) {
      String queryText = request.getQueryText();
      String separator = builder.length() == 0 ? "" : batchQuerySeparator();
      if (builder.length() + separator.length() + queryText.length() > LIMIT) {
        break;
      }
      builder.append(separator).append(queryText);
    }
    return builder.toString();
  }

  private static String batchQuerySeparator() {
    return emitStableDatabaseSemconv() ? "; " : ";";
  }
}

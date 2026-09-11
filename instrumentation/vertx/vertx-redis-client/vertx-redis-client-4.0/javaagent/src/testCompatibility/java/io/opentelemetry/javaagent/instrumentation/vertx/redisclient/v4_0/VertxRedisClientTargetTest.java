/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.sdk.trace.data.SpanData;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Request;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class VertxRedisClientTargetTest extends AbstractVertxRedisClientTargetTest {

  @Test
  void optionsReuseDoesNotChangeClientTarget() {
    RedisOptions options = new RedisOptions().setConnectionString("redis://" + host + ":" + port);
    Redis client = Redis.createClient(vertx, options);
    cleanup.deferCleanup(client::close);

    options.setConnectionString("redis://other:1234");
    Redis otherClient = Redis.createClient(vertx, options);
    cleanup.deferCleanup(otherClient::close);

    client
        .send(Request.cmd(Command.SET).arg("options-reuse").arg("value"))
        .toCompletionStage()
        .toCompletableFuture()
        .join();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<SpanData> spans =
                  testing.spans().stream()
                      .filter(span -> span.getName().startsWith("SET"))
                      .collect(toList());
              assertThat(spans).hasSize(1);
              assertThat(spans.get(0).getAttributes().get(SERVER_ADDRESS)).isEqualTo(host);
              assertThat(spans.get(0).getAttributes().get(SERVER_PORT))
                  .isEqualTo(Long.valueOf(port));
            });
  }
}

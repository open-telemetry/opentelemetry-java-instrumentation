/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.RedisCommandExecutionException;
import com.lambdaworks.redis.RedisCommandTimeoutException;
import com.lambdaworks.redis.codec.Utf8StringCodec;
import com.lambdaworks.redis.output.StatusOutput;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;

class LettuceBatchAttributesGetterTest {

  private final LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

  @Test
  void batchUsesConfiguredTarget() {
    LettuceBatchRequest request =
        LettuceBatchRequest.create(
            singletonList(command()), null, RedisServerTarget.ofEndpoint("configured-node:6379"));

    assertThat(getter.getServerAddress(request)).isEqualTo("configured-node");
    assertThat(getter.getServerPort(request)).isEqualTo(null);
  }

  @Test
  void batchErrorUsesRequestOrderUnlessClientFails() {
    Throwable first = new RedisCommandExecutionException("");
    Throwable second = new RedisCommandExecutionException("Second.Code! details");
    Throwable client = new RedisCommandTimeoutException("WRONGTYPE client timeout");

    assertThat(LettuceBatchContext.getError(null, new Throwable[] {null, first, second}))
        .isSameAs(first);
    assertThat(LettuceBatchContext.getError(null, new Throwable[] {first, client}))
        .isSameAs(client);
    assertThat(LettuceBatchContext.getError(null, new Throwable[] {client, first}))
        .isSameAs(client);
    assertThat(LettuceBatchContext.getError(second, new Throwable[] {first})).isSameAs(second);
    assertThat(LettuceBatchContext.getError(client, new Throwable[] {first})).isSameAs(client);
    assertThat(LettuceBatchContext.getError(null, new Throwable[] {null, null})).isNull();
  }

  private static RedisCommand<String, String, String> command() {
    Utf8StringCodec codec = new Utf8StringCodec();
    return new Command<>(CommandType.GET, new StatusOutput<>(codec));
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisLoadingException;
import org.redisson.client.RedisOutOfMemoryException;

class RedissonDbAttributesGetterTest {

  private static final RedissonDbAttributesGetter GETTER = new RedissonDbAttributesGetter();
  private static final RedissonRequest REQUEST =
      RedissonRequest.create(null, new Object(), null, null);
  private static final AttributesExtractor<RedissonRequest, Void> EXTRACTOR =
      DbClientAttributesExtractor.create(GETTER);

  @ParameterizedTest
  @MethodSource("redisErrorMessages")
  void extractsKnownRedisErrorTypes(String message, String expectedErrorType) {
    assertThat(GETTER.getErrorType(REQUEST, null, new RedisException(message)))
        .isEqualTo(expectedErrorType);
  }

  private static Stream<Arguments> redisErrorMessages() {
    return Stream.of(
        argumentSet(
            "generic error",
            "ERR unknown command with variable details. channel: redis command: GET",
            "ERR"),
        argumentSet(
            "wrong type",
            "WRONGTYPE Operation against a key holding the wrong kind of value. channel: redis command: GET",
            "WRONGTYPE"),
        argumentSet("malformed prefix", "ERR", null),
        argumentSet("unknown prefix", "CUSTOM variable details. channel: redis command: GET", null),
        argumentSet("message without decoder marker", "ERR connection refused", null),
        argumentSet("non-server error", "Unable to decode response. channel: redis", null));
  }

  @Test
  void missingMessageHasNoErrorType() {
    assertThat(GETTER.getErrorType(REQUEST, null, new RedisException())).isNull();
    assertThat(GETTER.getErrorType(REQUEST, null, null)).isNull();
  }

  @Test
  void dedicatedExceptionKeepsItsExceptionClassFallback() {
    RedisLoadingException error = new RedisLoadingException("LOADING Redis is loading");

    assertThat(GETTER.getErrorType(REQUEST, null, error)).isNull();
    assertThat(extractedErrorType(error)).isEqualTo(RedisLoadingException.class.getName());
  }

  @Test
  void dedicatedExceptionWithRemovedPrefixKeepsItsExceptionClassFallback() {
    RedisOutOfMemoryException error =
        new RedisOutOfMemoryException("maxmemory threshold exceeded. channel: redis command: SET");

    assertThat(GETTER.getErrorType(REQUEST, null, error)).isNull();
    assertThat(extractedErrorType(error)).isEqualTo(RedisOutOfMemoryException.class.getName());
  }

  @Test
  void connectionExceptionIsNotClassifiedAsServerError() {
    RedisConnectionException error = new RedisConnectionException("ERR connection refused");

    assertThat(GETTER.getErrorType(REQUEST, null, error)).isNull();
    assertThat(extractedErrorType(error)).isEqualTo(RedisConnectionException.class.getName());
  }

  private static String extractedErrorType(Throwable error) {
    AttributesBuilder attributes = Attributes.builder();
    EXTRACTOR.onEnd(attributes, Context.root(), REQUEST, null, error);
    return attributes.build().get(ERROR_TYPE);
  }
}

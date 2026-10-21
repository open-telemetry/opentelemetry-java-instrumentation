/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.vertx.redis.client.Response;
import io.vertx.redis.client.impl.types.ErrorType;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VertxRedisClientErrorTypeTest {

  @ParameterizedTest
  @MethodSource("serverErrorReplies")
  void extractsServerErrorPrefix(ErrorType error, String expected) {
    assertThat(VertxRedisClientAttributesGetter.getRedisErrorType(error)).isEqualTo(expected);
  }

  private static Stream<Arguments> serverErrorReplies() {
    String longPrefix = String.join("", Collections.nCopies(65, "x"));
    return Stream.of(
        argumentSet(
            "wrongtype",
            ErrorType.create("WRONGTYPE Operation against a key holding the wrong kind of value"),
            "WRONGTYPE"),
        argumentSet("punctuation", ErrorType.create("Custom.Code! detail"), "Custom.Code!"),
        argumentSet("lowercase", ErrorType.create("lowercase detail"), "lowercase"),
        argumentSet("longPrefix", ErrorType.create(longPrefix + " detail"), longPrefix),
        argumentSet("prefixOnly", ErrorType.create("PrefixOnly"), "PrefixOnly"),
        argumentSet("leadingSpaces", ErrorType.create("  Prefix detail"), "Prefix"),
        argumentSet("tabIsNotSeparator", ErrorType.create("Custom\tCode detail"), "Custom\tCode"),
        argumentSet("crlfTerminator", ErrorType.create("Custom.Code!\r\nignored"), "Custom.Code!"),
        argumentSet("emptyPrefix", ErrorType.create(" "), null),
        argumentSet("emptyReply", ErrorType.create(""), null),
        argumentSet("batchError", ErrorType.create("ERR [0] WRONGTYPE detail"), "WRONGTYPE"));
  }

  @Test
  void doesNotClassifyClientFailuresAsServerErrors() {
    assertThat(
            VertxRedisClientAttributesGetter.getRedisErrorType(
                new IllegalStateException("WRONGTYPE detail")))
        .isNull();
    assertThat(
            VertxRedisClientAttributesGetter.getRedisErrorType(
                ErrorType.create("CONNECTION_CLOSED")))
        .isNull();
    assertThat(VertxRedisClientAttributesGetter.getRedisErrorType(null)).isNull();
  }

  @Test
  void findsServerErrorsInBatchResponses() {
    ErrorType error = ErrorType.create("WRONGTYPE detail");
    Iterable<Response> responses = singletonList(error);

    assertThat(VertxRedisClientAttributesGetter.getResponseError(responses)).isSameAs(error);
  }
}

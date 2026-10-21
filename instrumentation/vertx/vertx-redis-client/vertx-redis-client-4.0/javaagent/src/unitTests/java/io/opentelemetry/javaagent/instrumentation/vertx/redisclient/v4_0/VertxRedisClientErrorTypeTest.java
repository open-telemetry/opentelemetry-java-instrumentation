/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.redis.client.Response;
import io.vertx.redis.client.impl.types.ErrorType;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VertxRedisClientErrorTypeTest {

  @ParameterizedTest
  @MethodSource("serverErrorReplies")
  void extractsServerErrorPrefix(String reply, String expected) {
    assertThat(VertxRedisClientError.parseErrorType(reply)).isEqualTo(expected);
  }

  private static Stream<Arguments> serverErrorReplies() {
    String longPrefix = String.join("", Collections.nCopies(65, "x"));
    return Stream.of(
        argumentSet(
            "wrongtype",
            "WRONGTYPE Operation against a key holding the wrong kind of value",
            "WRONGTYPE"),
        argumentSet("punctuation", "Custom.Code! detail", "Custom.Code!"),
        argumentSet("lowercase", "lowercase detail", "lowercase"),
        argumentSet("longPrefix", longPrefix + " detail", longPrefix),
        argumentSet("prefixOnly", "PrefixOnly", "PrefixOnly"),
        argumentSet("leadingSpaces", "  Prefix detail", "Prefix"),
        argumentSet("tabIsNotSeparator", "Custom\tCode detail", "Custom\tCode"),
        argumentSet("unicode", "échec detail", "échec"),
        argumentSet("emptyPrefix", " ", null),
        argumentSet("emptyReply", "", null),
        argumentSet("nullReply", null, null),
        argumentSet("realErrReply", "ERR [0] application detail", "ERR"));
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
    assertThat(
            VertxRedisClientAttributesGetter.getRedisErrorType(
                ErrorType.create("ILLEGAL_STATE Unknown RESP type ?")))
        .isNull();
    assertThat(
            VertxRedisClientAttributesGetter.getRedisErrorType(
                ErrorType.create("Invalid boolean value: x")))
        .isNull();
    assertThat(
            VertxRedisClientAttributesGetter.getRedisErrorType(
                ErrorType.create("ERR [0] CONNECTION_CLOSED")))
        .isNull();
    assertThat(VertxRedisClientAttributesGetter.getRedisErrorType(null)).isNull();
  }

  @Test
  void findsServerErrorsInBatchResponses() {
    ErrorType error = ErrorType.create("WRONGTYPE detail");
    Iterable<Response> responses = singletonList(error);

    assertThat(VertxRedisClientAttributesGetter.getResponseError(responses)).isSameAs(error);
  }

  @ParameterizedTest
  @MethodSource("batchErrors")
  void preservesOriginalBatchErrorsAndRequestOrder(
      String firstReply, String separator, String expected) {
    Promise<Object> promise = Promise.promise();
    AtomicInteger remaining = new AtomicInteger(2);
    ErrorType first = ErrorType.create(new String(firstReply.getBytes(UTF_8), ISO_8859_1));
    ErrorType second = ErrorType.create("Second");
    VertxRedisClientError.captureServerError(first);
    VertxRedisClientError.captureServerError(second);

    VertxRedisClientError.recordBatchResult(promise, 1, Future.failedFuture(second), remaining);
    remaining.decrementAndGet();
    VertxRedisClientError.BatchCompletion completion =
        VertxRedisClientError.recordBatchResult(promise, 0, Future.failedFuture(first), remaining);
    remaining.decrementAndGet();
    ErrorType aggregate =
        ErrorType.create(
            "ERR [1] Second"
                + separator
                + "ERR [0] "
                + (first.is("ERR") ? first.getMessage().substring(4) : first.getMessage()));
    VertxRedisClientError.captureBatchError(aggregate, completion);

    assertThat(VertxRedisClientAttributesGetter.getRedisErrorType(aggregate)).isEqualTo(expected);
    ErrorType callbackError = ErrorType.create("Application detail");
    VertxRedisClientError.captureBatchError(callbackError, completion);
    assertThat(VertxRedisClientAttributesGetter.getRedisErrorType(callbackError)).isNull();
    promise.fail(aggregate);
    assertThat(
            VirtualField.find(Promise.class, VertxRedisClientError.BatchErrors.class).get(promise))
        .isNull();
    assertThat(
            VertxRedisClientError.recordBatchResult(
                promise, 0, Future.failedFuture(first), remaining))
        .isNull();
  }

  private static Stream<Arguments> batchErrors() {
    return Stream.of(
        argumentSet("lfOriginalErr", "ERR original detail", "\n", "ERR"),
        argumentSet("crlfOriginalErr", "ERR original detail", "\r\n", "ERR"),
        argumentSet("lfPrefixOnly", "First", "\n", "First"),
        argumentSet("crlfPrefixOnly", "First", "\r\n", "First"),
        argumentSet("lfUnicode", "错误 detail", "\n", "错误"),
        argumentSet("crlfUnicode", "错误 detail", "\r\n", "错误"));
  }

  @Test
  void wrappedClientFailureWinsOverEarlierServerError() {
    Promise<Object> promise = Promise.promise();
    AtomicInteger remaining = new AtomicInteger(2);
    ErrorType serverError = ErrorType.create("First detail");
    VertxRedisClientError.captureServerError(serverError);
    VertxRedisClientError.recordBatchResult(
        promise, 0, Future.failedFuture(serverError), remaining);
    remaining.decrementAndGet();
    VertxRedisClientError.BatchCompletion completion =
        VertxRedisClientError.recordBatchResult(
            promise, 1, Future.failedFuture(ErrorType.create("CONNECTION_CLOSED")), remaining);
    remaining.decrementAndGet();
    ErrorType aggregate = ErrorType.create("ERR [0] First detail\nERR [1] CONNECTION_CLOSED");
    VertxRedisClientError.captureBatchError(aggregate, completion);

    assertThat(VertxRedisClientAttributesGetter.getRedisErrorType(aggregate)).isNull();
    promise.fail(aggregate);
    assertThat(
            VirtualField.find(Promise.class, VertxRedisClientError.BatchErrors.class).get(promise))
        .isNull();
  }
}

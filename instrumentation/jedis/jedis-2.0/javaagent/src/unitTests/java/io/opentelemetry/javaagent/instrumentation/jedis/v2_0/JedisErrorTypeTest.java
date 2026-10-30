/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

class JedisErrorTypeTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void confirmedServerErrorUsesPrefix(boolean wrapped) {
    JedisDataException serverError = new JedisDataException("WRONGTYPE details");
    JedisRequest request = requestFor(serverError);
    Throwable error = wrapped ? new JedisDataException(serverError) : serverError;

    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, error))
        .isEqualTo("WRONGTYPE");
  }

  @Test
  void unconfirmedCauseKeepsOriginalExceptionClassFallback() {
    JedisDataException serverError = new JedisDataException("WRONGTYPE details");
    JedisRequest request = requestFor(serverError);
    JedisDataException error =
        new JedisDataException(new JedisConnectionException("client failure"));

    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, error))
        .isEqualTo(error.getClass().getName());
  }

  @Test
  void clientFailureWithServerCauseKeepsExceptionClassFallback() {
    JedisDataException serverError = new JedisDataException("WRONGTYPE details");
    JedisRequest request = requestFor(serverError);
    JedisConnectionException error = new JedisConnectionException(serverError);

    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, error))
        .isEqualTo(error.getClass().getName());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void dedicatedServerErrorKeepsOriginalExceptionClassFallback(boolean wrapped) {
    JedisDataException serverError = new JedisDataException("WRONGTYPE details") {};
    JedisRequest request = requestFor(serverError);
    Throwable error = wrapped ? new JedisDataException(serverError) : serverError;

    assertThat(JedisServerError.errorType(serverError)).isNull();
    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, error))
        .isEqualTo(error.getClass().getName());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "WRONGTYPEé details"})
  void unusableServerErrorPrefixKeepsExceptionClassFallback(String message) {
    JedisDataException serverError = new JedisDataException(message);
    JedisRequest request = requestFor(serverError);

    assertThat(JedisServerError.errorType(serverError)).isNull();
    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, serverError))
        .isEqualTo(serverError.getClass().getName());
  }

  @Test
  void successfulCommandHasNoErrorType() {
    JedisRequest request = requestFor(new JedisDataException("WRONGTYPE details"));

    assertThat(new JedisDbAttributesGetter().getErrorType(request, null, null)).isNull();
  }

  private static JedisRequest requestFor(Throwable serverError) {
    JedisRequest request = JedisRequest.create(new Connection(), Protocol.Command.GET);
    request.setServerErrorContext(error -> error == serverError);
    return request;
  }
}

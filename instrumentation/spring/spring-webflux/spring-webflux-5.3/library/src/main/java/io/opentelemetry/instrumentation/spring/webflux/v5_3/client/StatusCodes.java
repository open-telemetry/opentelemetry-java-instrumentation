/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.client;

import static java.util.logging.Level.FINE;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;

final class StatusCodes {

  private static final Logger logger = Logger.getLogger(StatusCodes.class.getName());

  @Nullable
  private static final Function<ClientResponse, Integer> statusCodeFunction =
      getStatusCodeFunction();

  @Nullable
  static Integer get(ClientResponse response) {
    if (statusCodeFunction == null) {
      return null;
    }
    return statusCodeFunction.apply(response);
  }

  @Nullable
  private static Function<ClientResponse, Integer> getStatusCodeFunction() {
    Function<ClientResponse, Integer> statusCodeFunction = getStatusCodeFunction60();
    if (statusCodeFunction != null) {
      return statusCodeFunction;
    }
    statusCodeFunction = getStatusCodeFunction51();
    if (statusCodeFunction != null) {
      return statusCodeFunction;
    }
    return getStatusCodeFunction50();
  }

  // in webflux 6.0, HttpStatusCode class was introduced, and statusCode() was changed to return
  // HttpStatusCode instead of HttpStatus
  @Nullable
  private static Function<ClientResponse, Integer> getStatusCodeFunction60() {
    MethodHandle statusCode;
    MethodHandle value;
    try {
      Class<?> httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatusCode");
      statusCode =
          MethodHandles.publicLookup()
              .findVirtual(
                  ClientResponse.class, "statusCode", MethodType.methodType(httpStatusCodeClass));
      value =
          MethodHandles.publicLookup()
              .findVirtual(httpStatusCodeClass, "value", MethodType.methodType(int.class));
    } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException e) {
      return null;
    }

    return response -> {
      try {
        Object httpStatusCode = statusCode.invoke(response);
        return (int) value.invoke(httpStatusCode);
      } catch (Throwable e) {
        logger.log(FINE, e.getMessage(), e);
        return null;
      }
    };
  }

  // in webflux 5.1, rawStatusCode() was introduced to retrieve the exact status code
  // note: rawStatusCode() was deprecated in 6.0
  @Nullable
  private static Function<ClientResponse, Integer> getStatusCodeFunction51() {
    MethodHandle rawStatusCode;
    try {
      rawStatusCode =
          MethodHandles.publicLookup()
              .findVirtual(ClientResponse.class, "rawStatusCode", MethodType.methodType(int.class));
    } catch (IllegalAccessException | NoSuchMethodException e) {
      return null;
    }

    return response -> {
      try {
        return (int) rawStatusCode.invoke(response);
      } catch (Throwable e) {
        logger.log(FINE, e.getMessage(), e);
        return null;
      }
    };
  }

  // in webflux 5.0, statusCode() returns HttpStatus, which only represents standard status codes
  // (there's no way to capture arbitrary status codes)
  @Nullable
  private static Function<ClientResponse, Integer> getStatusCodeFunction50() {
    MethodHandle statusCode;
    MethodHandle value;
    try {
      statusCode =
          MethodHandles.publicLookup()
              .findVirtual(
                  ClientResponse.class, "statusCode", MethodType.methodType(HttpStatus.class));
      value =
          MethodHandles.publicLookup()
              .findVirtual(HttpStatus.class, "value", MethodType.methodType(int.class));
    } catch (IllegalAccessException | NoSuchMethodException e) {
      return null;
    }

    return response -> {
      try {
        Object httpStatusCode = statusCode.invoke(response);
        return (int) value.invoke(httpStatusCode);
      } catch (Throwable e) {
        logger.log(FINE, e.getMessage(), e);
        return null;
      }
    };
  }

  private StatusCodes() {}
}

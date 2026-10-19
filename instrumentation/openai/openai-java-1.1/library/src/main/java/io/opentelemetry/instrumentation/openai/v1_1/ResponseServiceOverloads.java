/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static java.lang.invoke.MethodType.methodType;

import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import javax.annotation.Nullable;

/**
 * Access to the {@code ResponseService} overloads that SDK 2.x adds as default methods: {@code
 * create()}, {@code create(RequestOptions)} and the {@code StructuredResponseCreateParams}
 * overloads. Their default implementations call {@code create(ResponseCreateParams,
 * RequestOptions)} on the delegate, so the proxies have to route them explicitly. The types don't
 * exist in the SDK version this library compiles against.
 */
final class ResponseServiceOverloads {

  @Nullable private static final MethodHandle NONE_HANDLE;
  @Nullable private static final Class<?> STRUCTURED_PARAMS_CLASS;
  @Nullable private static final MethodHandle RAW_PARAMS_HANDLE;
  @Nullable private static final MethodHandle RESPONSE_TYPE_HANDLE;
  @Nullable private static final MethodHandle STRUCTURED_RESPONSE_CONSTRUCTOR;

  static {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    ClassLoader classLoader = ResponseServiceOverloads.class.getClassLoader();

    MethodHandle none;
    try {
      none =
          lookup.findStatic(
              ResponseCreateParams.class, "none", methodType(ResponseCreateParams.class));
    } catch (Exception ignored) {
      none = null;
    }
    NONE_HANDLE = none;

    Class<?> structuredParamsClass;
    MethodHandle rawParams;
    MethodHandle responseType;
    MethodHandle structuredResponseConstructor;
    try {
      structuredParamsClass =
          Class.forName(
              "com.openai.models.responses.StructuredResponseCreateParams", false, classLoader);
      Class<?> structuredResponseClass =
          Class.forName("com.openai.models.responses.StructuredResponse", false, classLoader);
      rawParams =
          lookup.findVirtual(
              structuredParamsClass, "rawParams", methodType(ResponseCreateParams.class));
      responseType =
          lookup.findVirtual(structuredParamsClass, "responseType", methodType(Class.class));
      structuredResponseConstructor =
          lookup.findConstructor(
              structuredResponseClass, methodType(void.class, Class.class, Response.class));
    } catch (Exception ignored) {
      structuredParamsClass = null;
      rawParams = null;
      responseType = null;
      structuredResponseConstructor = null;
    }
    STRUCTURED_PARAMS_CLASS = structuredParamsClass;
    RAW_PARAMS_HANDLE = rawParams;
    RESPONSE_TYPE_HANDLE = responseType;
    STRUCTURED_RESPONSE_CONSTRUCTOR = structuredResponseConstructor;
  }

  /** Returns the params {@code create()} sends, or null if the SDK has no such overload. */
  @Nullable
  static ResponseCreateParams emptyParams() throws Throwable {
    if (NONE_HANDLE == null) {
      return null;
    }
    return (ResponseCreateParams) NONE_HANDLE.invoke();
  }

  static boolean isStructuredParams(Class<?> type) {
    return STRUCTURED_PARAMS_CLASS != null && type == STRUCTURED_PARAMS_CLASS;
  }

  static ResponseCreateParams rawParams(Object structuredParams) throws Throwable {
    // isStructuredParams() returned true, so the handles are set
    return (ResponseCreateParams) invoke(RAW_PARAMS_HANDLE, structuredParams);
  }

  /** Builds the {@code StructuredResponse<T>} the SDK's default method would have returned. */
  static Object toStructuredResponse(Object structuredParams, Response response) throws Throwable {
    Object responseType = invoke(RESPONSE_TYPE_HANDLE, structuredParams);
    return invoke(STRUCTURED_RESPONSE_CONSTRUCTOR, responseType, response);
  }

  private static Object invoke(@Nullable MethodHandle handle, Object... args) throws Throwable {
    if (handle == null) {
      throw new IllegalStateException("structured response support is not available");
    }
    return handle.invokeWithArguments(args);
  }

  private ResponseServiceOverloads() {}
}

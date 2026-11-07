/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.AgentClassLoaderAccess;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.spy.memcached.internal.BulkGetFuture;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationException;
import net.spy.memcached.ops.OperationStatus;
import net.spy.memcached.ops.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SpymemcachedFutureStatusInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @AfterEach
  void shutdownExecutor() throws InterruptedException {
    executor.shutdownNow();
    assertThat(executor.awaitTermination(1, SECONDS)).isTrue();
  }

  @Test
  void futureStatusSettersAreCapturedByTheInstalledAgent()
      throws InterruptedException, ReflectiveOperationException {
    OperationStatus errorStatus = new OperationStatus(false, "too large", StatusCode.ERR_2BIG);
    ClassLoader instrumentationClassLoader = instrumentationClassLoader();
    Method getResponseStatus = getResponseStatusMethod(instrumentationClassLoader);

    OperationFuture<Boolean> operationFuture =
        new OperationFuture<>("key", new CountDownLatch(1), 1, executor);
    operationFuture.setOperation(mock(Operation.class));
    operationFuture.set(false, errorStatus);
    assertThat(getResponseStatus(getResponseStatus, operationFuture).getStatusCode())
        .isEqualTo(StatusCode.ERR_2BIG);
    assertThat(
            getAttribute(
                instrumentationClassLoader, "getDbResponseStatusCode", operationFuture, null))
        .isEqualTo("ERR_2BIG");
    assertThat(
            getAttribute(
                instrumentationClassLoader,
                "getErrorType",
                operationFuture,
                new OperationException()))
        .isEqualTo("ERR_2BIG");

    GetFuture<Object> getFuture = new GetFuture<>(new CountDownLatch(0), 1, "key", executor);
    getFuture.setOperation(mock(Operation.class));
    CompletableFuture<Object> result = new CompletableFuture<>();
    result.completeExceptionally(new IllegalStateException("result retrieval failed"));
    getFuture.set(result, errorStatus);
    ExecutionException retrievalError;
    try {
      getFuture.get();
      throw new AssertionError("Expected result retrieval to fail");
    } catch (ExecutionException e) {
      retrievalError = e;
    }
    assertThat(retrievalError).hasCauseInstanceOf(IllegalStateException.class);
    assertThat(getResponseStatus(getResponseStatus, getFuture).getStatusCode())
        .isEqualTo(StatusCode.ERR_2BIG);
    assertThat(
            getAttribute(
                instrumentationClassLoader, "getDbResponseStatusCode", getFuture, retrievalError))
        .isEqualTo("ERR_2BIG");
    assertThat(getAttribute(instrumentationClassLoader, "getErrorType", getFuture, retrievalError))
        .isNull();

    BulkGetFuture<?> bulkFuture =
        new BulkGetFuture<>(emptyMap(), emptyList(), new CountDownLatch(1), executor);
    bulkFuture.setStatus(errorStatus);
    assertThat(getResponseStatus(getResponseStatus, bulkFuture).getStatusCode())
        .isEqualTo(StatusCode.ERR_2BIG);
    assertThat(
            getAttribute(instrumentationClassLoader, "getDbResponseStatusCode", bulkFuture, null))
        .isEqualTo("ERR_2BIG");
    assertThat(getAttribute(instrumentationClassLoader, "getErrorType", bulkFuture, null))
        .isEqualTo("ERR_2BIG");
  }

  @Test
  void expectedAndCancelledStatusesRemainOmitted() throws ReflectiveOperationException {
    ClassLoader instrumentationClassLoader = instrumentationClassLoader();
    Method getResponseStatus = getResponseStatusMethod(instrumentationClassLoader);
    OperationFuture<Boolean> successFuture =
        new OperationFuture<>("key", new CountDownLatch(1), 1, executor);
    successFuture.setOperation(mock(Operation.class));
    successFuture.set(true, new OperationStatus(true, "stored", StatusCode.SUCCESS));

    BulkGetFuture<?> cancelledFuture =
        new BulkGetFuture<>(emptyMap(), emptyList(), new CountDownLatch(1), executor);
    cancelledFuture.cancel(false);

    assertThat(getResponseStatus(getResponseStatus, successFuture).getStatusCode())
        .isEqualTo(StatusCode.SUCCESS);
    assertThat(getResponseStatus(getResponseStatus, cancelledFuture)).isNull();
    assertThat(
            getAttribute(
                instrumentationClassLoader, "getDbResponseStatusCode", successFuture, null))
        .isNull();
    assertThat(getAttribute(instrumentationClassLoader, "getErrorType", successFuture, null))
        .isNull();
    assertThat(
            getAttribute(
                instrumentationClassLoader, "getDbResponseStatusCode", cancelledFuture, null))
        .isNull();
    assertThat(getAttribute(instrumentationClassLoader, "getErrorType", cancelledFuture, null))
        .isNull();
  }

  private static OperationStatus getResponseStatus(Method method, Object future)
      throws ReflectiveOperationException {
    return (OperationStatus) method.invoke(null, future);
  }

  private static String getAttribute(
      ClassLoader instrumentationClassLoader, String methodName, Object future, Throwable error)
      throws ReflectiveOperationException {
    Class<?> getterClass =
        instrumentationClassLoader.loadClass(
            "io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12.SpymemcachedAttributesGetter");
    Constructor<?> constructor = getterClass.getDeclaredConstructor();
    constructor.setAccessible(true);
    Object getter = constructor.newInstance();
    Class<?> requestClass =
        instrumentationClassLoader.loadClass(
            "io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12.SpymemcachedRequest");
    Method method = getterClass.getMethod(methodName, requestClass, Object.class, Throwable.class);
    method.setAccessible(true);
    return (String) method.invoke(getter, null, future, error);
  }

  private static ClassLoader instrumentationClassLoader() throws ReflectiveOperationException {
    Class<?> registryClass =
        AgentClassLoaderAccess.loadClass(
            "io.opentelemetry.javaagent.tooling.instrumentation.indy.InstrumentationClassLoaderRegistry");
    return (ClassLoader)
        registryClass
            .getMethod("getInstrumentationClassLoader", String.class, ClassLoader.class)
            .invoke(
                null,
                "io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12.SpymemcachedInstrumentationModule",
                OperationFuture.class.getClassLoader());
  }

  private static Method getResponseStatusMethod(ClassLoader instrumentationClassLoader)
      throws ReflectiveOperationException {
    Class<?> singletons =
        instrumentationClassLoader.loadClass(
            "io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12.SpymemcachedSingletons");
    Method method = singletons.getDeclaredMethod("getResponseStatus", Object.class);
    method.setAccessible(true);
    return method;
  }
}

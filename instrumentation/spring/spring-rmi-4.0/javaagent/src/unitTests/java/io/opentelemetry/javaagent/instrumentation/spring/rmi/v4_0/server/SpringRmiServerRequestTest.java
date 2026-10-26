/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class SpringRmiServerRequestTest {

  public interface FirstService {
    String duplicate(String value);

    String overloaded(String value);

    String noArguments();
  }

  public interface SecondService {
    String duplicate(String value);

    String overloaded(Integer value);
  }

  public interface DuplicateService extends FirstService, SecondService {}

  @Test
  void resolvesDuplicateDeclarationsWithInterfaceLookup() throws Exception {
    Method method = DuplicateService.class.getMethod("duplicate", String.class);

    assertThat(rpcMethod(DuplicateService.class, "duplicate", String.class))
        .isEqualTo(method.getDeclaringClass().getName() + "/" + method.getName());
  }

  @Test
  void distinguishesOverloadsAndCopiesParameterTypes() throws Exception {
    Class<?>[] parameterTypes = {String.class};
    SpringRmiServerRequest request =
        createRequest(DuplicateService.class, "overloaded", parameterTypes);
    parameterTypes[0] = Integer.class;

    assertThat(request.rpcMethod()).isEqualTo(FirstService.class.getName() + "/overloaded");
    assertThat(rpcMethod(DuplicateService.class, "overloaded", Integer.class))
        .isEqualTo(SecondService.class.getName() + "/overloaded");
  }

  @Test
  void handlesNullParameterTypesAndMissingLookups() {
    assertThat(rpcMethod(DuplicateService.class, "noArguments", (Class<?>[]) null))
        .isEqualTo(FirstService.class.getName() + "/noArguments");
    assertThat(rpcMethod(DuplicateService.class, "missing")).isEqualTo("_OTHER");
    assertThat(createRequest(DuplicateService.class, "missing").rpcMethodOriginal())
        .isEqualTo(DuplicateService.class.getName() + "/missing");
    assertThat(rpcMethod(DuplicateService.class, "duplicate", Integer.class)).isEqualTo("_OTHER");
    assertThat(
            createRequest(DuplicateService.class, "duplicate", Integer.class).rpcMethodOriginal())
        .isEqualTo(DuplicateService.class.getName() + "/duplicate");
    assertThat(rpcMethod(null, "missing", (Class<?>[]) null)).isEqualTo("_OTHER");
    assertThat(createRequest(null, "missing").rpcMethodOriginal()).isEqualTo("missing");
    assertThat(createRequest(DuplicateService.class, "noArguments").rpcMethodOriginal()).isNull();
  }

  private static String rpcMethod(
      Class<?> serviceInterface, String methodName, Class<?>... parameterTypes) {
    return createRequest(serviceInterface, methodName, parameterTypes).rpcMethod();
  }

  private static SpringRmiServerRequest createRequest(
      Class<?> serviceInterface, String methodName, Class<?>... parameterTypes) {
    return new SpringRmiServerRequest(
        ClassAndMethod.create(Object.class, methodName),
        serviceInterface,
        methodName,
        parameterTypes);
  }
}

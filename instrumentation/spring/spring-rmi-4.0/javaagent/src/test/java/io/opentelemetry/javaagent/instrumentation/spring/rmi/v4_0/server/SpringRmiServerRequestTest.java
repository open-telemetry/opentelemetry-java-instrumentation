/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.testing.common.AgentClassLoaderAccess;
import java.lang.reflect.Constructor;
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
    Object request = createRequest(DuplicateService.class, "overloaded", parameterTypes);
    parameterTypes[0] = Integer.class;

    assertThat(rpcMethod(request)).isEqualTo(FirstService.class.getName() + "/overloaded");
    assertThat(rpcMethod(DuplicateService.class, "overloaded", Integer.class))
        .isEqualTo(SecondService.class.getName() + "/overloaded");
  }

  @Test
  void handlesNullParameterTypesAndMissingLookups() throws Exception {
    assertThat(rpcMethod(DuplicateService.class, "noArguments", (Class<?>[]) null))
        .isEqualTo(FirstService.class.getName() + "/noArguments");
    assertThat(rpcMethod(DuplicateService.class, "missing")).isNull();
    assertThat(rpcMethod(DuplicateService.class, "duplicate", Integer.class)).isNull();
    assertThat(rpcMethod(null, "missing", (Class<?>[]) null)).isNull();
  }

  private static String rpcMethod(
      Class<?> serviceInterface, String methodName, Class<?>... parameterTypes) throws Exception {
    return rpcMethod(createRequest(serviceInterface, methodName, parameterTypes));
  }

  private static String rpcMethod(Object request) throws Exception {
    Class<?> requestClass = request.getClass();
    Method rpcMethod = requestClass.getDeclaredMethod("rpcMethod");
    rpcMethod.setAccessible(true);
    return (String) rpcMethod.invoke(request);
  }

  private static Object createRequest(
      Class<?> serviceInterface, String methodName, Class<?>... parameterTypes) throws Exception {
    Class<?> requestClass =
        AgentClassLoaderAccess.loadClass(
            "io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server."
                + "SpringRmiServerRequest");
    Constructor<?> constructor = requestClass.getDeclaredConstructors()[0];
    Class<?> classAndMethodClass = constructor.getParameterTypes()[0];
    constructor.setAccessible(true);
    Object legacyRequest =
        classAndMethodClass
            .getMethod("create", Class.class, String.class)
            .invoke(null, Object.class, methodName);
    return constructor.newInstance(legacyRequest, serviceInterface, methodName, parameterTypes);
  }
}

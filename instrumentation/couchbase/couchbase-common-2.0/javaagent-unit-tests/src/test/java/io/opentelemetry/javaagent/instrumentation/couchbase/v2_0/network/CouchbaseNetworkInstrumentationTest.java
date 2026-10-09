/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network;

import static net.bytebuddy.matcher.ElementMatchers.isBridge;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CouchbaseNetworkInstrumentationTest {

  @ParameterizedTest
  @MethodSource("instrumentations")
  void rejectsObjectBridge(TypeInstrumentation instrumentation) {
    List<ElementMatcher<? super MethodDescription>> matchers = new ArrayList<>();
    TypeTransformer transformer = mock(TypeTransformer.class);
    doAnswer(
            invocation -> {
              matchers.add(invocation.getArgument(0));
              return null;
            })
        .when(transformer)
        .applyAdviceToMethod(any(), anyString());
    instrumentation.transform(transformer);

    assertThat(matchers).hasSize(1);
    ElementMatcher<? super MethodDescription> matcher = matchers.get(0);
    MethodList<MethodDescription.InDefinedShape> methods =
        TypePool.Default.ofSystemLoader()
            .describe("com.couchbase.client.core.endpoint.AbstractGenericHandler")
            .resolve()
            .getDeclaredMethods()
            .filter(named("encode"));

    assertThat(
            matcher.matches(
                methods
                    .filter(
                        takesArgument(
                            1, named("com.couchbase.client.core.message.CouchbaseRequest")))
                    .getOnly()))
        .isTrue();
    assertThat(
            matcher.matches(
                methods.filter(isBridge().and(takesArgument(1, Object.class))).getOnly()))
        .isFalse();
  }

  private static Stream<TypeInstrumentation> instrumentations() {
    return Stream.of(
        new Couchbase20NetworkInstrumentation(), new Couchbase26NetworkInstrumentation());
  }
}

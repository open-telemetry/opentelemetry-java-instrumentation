/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient.v7_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KubernetesRequestUtilsTest {

  @Test
  void isResourceRequest() {
    assertThat(KubernetesRequestDigest.isResourceRequest("/api")).isFalse();
    assertThat(KubernetesRequestDigest.isResourceRequest("/apis")).isFalse();
    assertThat(KubernetesRequestDigest.isResourceRequest("/apis/v1")).isFalse();
    assertThat(KubernetesRequestDigest.isResourceRequest("/healthz")).isFalse();
    assertThat(KubernetesRequestDigest.isResourceRequest("/swagger.json")).isFalse();
    assertThat(KubernetesRequestDigest.isResourceRequest("/api/v1")).isFalse();
    assertThat(KubernetesRequestDigest.isResourceRequest("/api/v1/")).isFalse();
    assertThat(KubernetesRequestDigest.isResourceRequest("/apis/apps/v1")).isFalse();
    assertThat(KubernetesRequestDigest.isResourceRequest("/apis/apps/v1/")).isFalse();

    assertThat(KubernetesRequestDigest.isResourceRequest("/apis/example.io/v1/foos")).isTrue();
    assertThat(
            KubernetesRequestDigest.isResourceRequest(
                "/apis/example.io/v1/namespaces/default/foos"))
        .isTrue();
    assertThat(KubernetesRequestDigest.isResourceRequest("/api/v1/namespaces")).isTrue();
    assertThat(KubernetesRequestDigest.isResourceRequest("/api/v1/pods")).isTrue();
    assertThat(KubernetesRequestDigest.isResourceRequest("/api/v1/namespaces/default/pods"))
        .isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parseCoreResourceArguments")
  void parseCoreResource(String name, String urlPath, KubernetesResource expected)
      throws ParseKubernetesResourceException {
    assertResourceEquals(KubernetesResource.parseCoreResource(urlPath), expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parseRegularResourceArguments")
  void parseRegularResource(String name, String urlPath, KubernetesResource expected)
      throws ParseKubernetesResourceException {
    assertResourceEquals(KubernetesResource.parseRegularResource(urlPath), expected);
  }

  private static void assertResourceEquals(KubernetesResource actual, KubernetesResource expected) {
    assertThat(actual.getApiGroup()).isEqualTo(expected.getApiGroup());
    assertThat(actual.getApiVersion()).isEqualTo(expected.getApiVersion());
    assertThat(actual.getResource()).isEqualTo(expected.getResource());
    assertThat(actual.getSubResource()).isEqualTo(expected.getSubResource());
    assertThat(actual.getNamespace()).isEqualTo(expected.getNamespace());
    assertThat(actual.getName()).isEqualTo(expected.getName());
  }

  @ParameterizedTest
  @MethodSource("k8sRequestVerbsArguments")
  void k8sRequestVerbs(
      String httpVerb,
      boolean hasNamePathParam,
      boolean hasWatchParam,
      KubernetesVerb kubernetesVerb) {
    assertThat(KubernetesVerb.of(httpVerb, hasNamePathParam, hasWatchParam))
        .isEqualTo(kubernetesVerb);
  }

  private static Stream<Arguments> k8sRequestVerbsArguments() {
    return Stream.of(
        Arguments.of("GET", true, false, KubernetesVerb.GET),
        Arguments.of("GET", false, true, KubernetesVerb.WATCH),
        Arguments.of("GET", false, false, KubernetesVerb.LIST),
        Arguments.of("POST", false, false, KubernetesVerb.CREATE),
        Arguments.of("PUT", false, false, KubernetesVerb.UPDATE),
        Arguments.of("PATCH", false, false, KubernetesVerb.PATCH),
        Arguments.of("DELETE", true, false, KubernetesVerb.DELETE),
        Arguments.of("DELETE", false, false, KubernetesVerb.DELETE_COLLECTION));
  }

  private static Stream<Arguments> parseRegularResourceArguments() {
    KubernetesResource deploymentsList =
        new KubernetesResource("apps", "v1", "deployments", null, null, null);
    KubernetesResource namespacedDeployments =
        new KubernetesResource("apps", "v1", "deployments", null, "default", null);
    KubernetesResource namedDeployment =
        new KubernetesResource("apps", "v1", "deployments", null, "default", "foo");
    KubernetesResource namedDeploymentStatus =
        new KubernetesResource("apps", "v1", "deployments", "status", "default", "foo");
    KubernetesResource foosList =
        new KubernetesResource("example.io", "v1alpha1", "foos", null, null, null);
    KubernetesResource namespacedFoos =
        new KubernetesResource("example.io", "v1alpha1", "foos", null, "default", null);
    KubernetesResource namedFoo =
        new KubernetesResource("example.io", "v1alpha1", "foos", null, "default", "foo");
    KubernetesResource namedFooStatus =
        new KubernetesResource("example.io", "v1alpha1", "foos", "status", "default", "foo");
    return Stream.of(
        arguments("cluster-scoped list", "/apis/apps/v1/deployments", deploymentsList),
        arguments(
            "namespaced list",
            "/apis/apps/v1/namespaces/default/deployments",
            namespacedDeployments),
        arguments(
            "namespaced named",
            "/apis/apps/v1/namespaces/default/deployments/foo",
            namedDeployment),
        arguments(
            "namespaced named subresource",
            "/apis/apps/v1/namespaces/default/deployments/foo/status",
            namedDeploymentStatus),
        arguments(
            "custom resource cluster-scoped list", "/apis/example.io/v1alpha1/foos", foosList),
        arguments(
            "custom resource namespaced list",
            "/apis/example.io/v1alpha1/namespaces/default/foos",
            namespacedFoos),
        arguments(
            "custom resource namespaced named",
            "/apis/example.io/v1alpha1/namespaces/default/foos/foo",
            namedFoo),
        arguments(
            "custom resource namespaced named subresource",
            "/apis/example.io/v1alpha1/namespaces/default/foos/foo/status",
            namedFooStatus));
  }

  private static Stream<Arguments> parseCoreResourceArguments() {
    KubernetesResource podsList = new KubernetesResource("", "v1", "pods", null, null, null);
    KubernetesResource namespacedPods =
        new KubernetesResource("", "v1", "pods", null, "default", null);
    KubernetesResource namedPod = new KubernetesResource("", "v1", "pods", null, "default", "foo");
    KubernetesResource namedPodExec =
        new KubernetesResource("", "v1", "pods", "exec", "default", "foo");
    return Stream.of(
        arguments("cluster-scoped list", "/api/v1/pods", podsList),
        arguments("namespaced list", "/api/v1/namespaces/default/pods", namespacedPods),
        arguments("namespaced named", "/api/v1/namespaces/default/pods/foo", namedPod),
        arguments(
            "namespaced named subresource",
            "/api/v1/namespaces/default/pods/foo/exec",
            namedPodExec));
  }
}

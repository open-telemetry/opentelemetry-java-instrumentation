/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient.v7_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KubernetesRequestUtilsTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("isResourceRequestArguments")
  void isResourceRequest(String name, String urlPath, boolean expected) {
    assertThat(KubernetesRequestDigest.isResourceRequest(urlPath)).isEqualTo(expected);
  }

  private static Stream<Arguments> isResourceRequestArguments() {
    return Stream.of(
        arguments("api root", "/api", false),
        arguments("apis root", "/apis", false),
        arguments("apis version only", "/apis/v1", false),
        arguments("healthz", "/healthz", false),
        arguments("swagger", "/swagger.json", false),
        arguments("core api version", "/api/v1", false),
        arguments("core api version trailing slash", "/api/v1/", false),
        arguments("group api version", "/apis/apps/v1", false),
        arguments("group api version trailing slash", "/apis/apps/v1/", false),
        arguments("custom resource list", "/apis/example.io/v1/foos", true),
        arguments(
            "custom resource namespaced list", "/apis/example.io/v1/namespaces/default/foos", true),
        arguments("core namespaces list", "/api/v1/namespaces", true),
        arguments("core pods list", "/api/v1/pods", true),
        arguments("core namespaced pods list", "/api/v1/namespaces/default/pods", true));
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("k8sRequestVerbsArguments")
  void k8sRequestVerbs(
      String name,
      String httpVerb,
      boolean hasNamePathParam,
      boolean hasWatchParam,
      KubernetesVerb kubernetesVerb) {
    assertThat(KubernetesVerb.of(httpVerb, hasNamePathParam, hasWatchParam))
        .isEqualTo(kubernetesVerb);
  }

  private static Stream<Arguments> k8sRequestVerbsArguments() {
    return Stream.of(
        arguments("GET named", "GET", true, false, KubernetesVerb.GET),
        arguments("GET watch", "GET", false, true, KubernetesVerb.WATCH),
        arguments("GET list", "GET", false, false, KubernetesVerb.LIST),
        arguments("POST create", "POST", false, false, KubernetesVerb.CREATE),
        arguments("PUT update", "PUT", false, false, KubernetesVerb.UPDATE),
        arguments("PATCH", "PATCH", false, false, KubernetesVerb.PATCH),
        arguments("DELETE named", "DELETE", true, false, KubernetesVerb.DELETE),
        arguments("DELETE collection", "DELETE", false, false, KubernetesVerb.DELETE_COLLECTION));
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

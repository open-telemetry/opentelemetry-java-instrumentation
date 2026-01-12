/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static org.assertj.core.api.Assertions.assertThat;

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

  @ParameterizedTest
  @MethodSource("parseCoreResourceArguments")
  void parseCoreResource(
      String urlPath,
      String apiGroup,
      String apiVersion,
      String resource,
      String subResource,
      String namespace,
      String name)
      throws ParseKubernetesResourceException {
    assertThat(KubernetesResource.parseCoreResource(urlPath).getApiGroup()).isEqualTo(apiGroup);
    assertThat(KubernetesResource.parseCoreResource(urlPath).getApiVersion()).isEqualTo(apiVersion);
    assertThat(KubernetesResource.parseCoreResource(urlPath).getResource()).isEqualTo(resource);
    assertThat(KubernetesResource.parseCoreResource(urlPath).getSubResource())
        .isEqualTo(subResource);
    assertThat(KubernetesResource.parseCoreResource(urlPath).getNamespace()).isEqualTo(namespace);
    assertThat(KubernetesResource.parseCoreResource(urlPath).getName()).isEqualTo(name);
  }

  @ParameterizedTest
  @MethodSource("parseRegularResourceArguments")
  void parseRegularResource(
      String urlPath,
      String apiGroup,
      String apiVersion,
      String resource,
      String subResource,
      String namespace,
      String name)
      throws ParseKubernetesResourceException {
    assertThat(KubernetesResource.parseRegularResource(urlPath).getApiGroup()).isEqualTo(apiGroup);
    assertThat(KubernetesResource.parseRegularResource(urlPath).getApiVersion())
        .isEqualTo(apiVersion);
    assertThat(KubernetesResource.parseRegularResource(urlPath).getResource()).isEqualTo(resource);
    assertThat(KubernetesResource.parseRegularResource(urlPath).getSubResource())
        .isEqualTo(subResource);
    assertThat(KubernetesResource.parseRegularResource(urlPath).getNamespace())
        .isEqualTo(namespace);
    assertThat(KubernetesResource.parseRegularResource(urlPath).getName()).isEqualTo(name);
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
    return Stream.of(
        Arguments.of("/apis/apps/v1/deployments", "apps", "v1", "deployments", null, null, null),
        Arguments.of(
            "/apis/apps/v1/namespaces/default/deployments",
            "apps",
            "v1",
            "deployments",
            null,
            "default",
            null),
        Arguments.of(
            "/apis/apps/v1/namespaces/default/deployments/foo",
            "apps",
            "v1",
            "deployments",
            null,
            "default",
            "foo"),
        Arguments.of(
            "/apis/apps/v1/namespaces/default/deployments/foo/status",
            "apps",
            "v1",
            "deployments",
            "status",
            "default",
            "foo"),
        Arguments.of(
            "/apis/example.io/v1alpha1/foos", "example.io", "v1alpha1", "foos", null, null, null),
        Arguments.of(
            "/apis/example.io/v1alpha1/namespaces/default/foos",
            "example.io",
            "v1alpha1",
            "foos",
            null,
            "default",
            null),
        Arguments.of(
            "/apis/example.io/v1alpha1/namespaces/default/foos/foo",
            "example.io",
            "v1alpha1",
            "foos",
            null,
            "default",
            "foo"),
        Arguments.of(
            "/apis/example.io/v1alpha1/namespaces/default/foos/foo/status",
            "example.io",
            "v1alpha1",
            "foos",
            "status",
            "default",
            "foo"));
  }

  private static Stream<Arguments> parseCoreResourceArguments() {
    return Stream.of(
        Arguments.of("/api/v1/pods", "", "v1", "pods", null, null, null),
        Arguments.of("/api/v1/namespaces/default/pods", "", "v1", "pods", null, "default", null),
        Arguments.of(
            "/api/v1/namespaces/default/pods/foo", "", "v1", "pods", null, "default", "foo"),
        Arguments.of(
            "/api/v1/namespaces/default/pods/foo/exec",
            "",
            "v1",
            "pods",
            "exec",
            "default",
            "foo"));
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient.v7_0;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KubernetesRequestUtilsTest {

  static final class ParseCase {
    final String name;
    final String urlPath;
    final Resource expected;

    ParseCase(String name, String urlPath, Resource expected) {
      this.name = name;
      this.urlPath = urlPath;
      this.expected = expected;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  static final class Resource {
    final String apiGroup;
    final String apiVersion;
    final String resource;
    final String subResource;
    final String namespace;
    final String name;

    Resource(
        String apiGroup,
        String apiVersion,
        String resource,
        String subResource,
        String namespace,
        String name) {
      this.apiGroup = apiGroup;
      this.apiVersion = apiVersion;
      this.resource = resource;
      this.subResource = subResource;
      this.namespace = namespace;
      this.name = name;
    }
  }

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
  void parseCoreResource(String name, ParseCase testCase) throws ParseKubernetesResourceException {
    KubernetesResource actual = KubernetesResource.parseCoreResource(testCase.urlPath);
    assertResourceEquals(actual, testCase.expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parseRegularResourceArguments")
  void parseRegularResource(String name, ParseCase testCase)
      throws ParseKubernetesResourceException {
    KubernetesResource actual = KubernetesResource.parseRegularResource(testCase.urlPath);
    assertResourceEquals(actual, testCase.expected);
  }

  private static void assertResourceEquals(KubernetesResource actual, Resource expected) {
    assertThat(actual.getApiGroup()).isEqualTo(expected.apiGroup);
    assertThat(actual.getApiVersion()).isEqualTo(expected.apiVersion);
    assertThat(actual.getResource()).isEqualTo(expected.resource);
    assertThat(actual.getSubResource()).isEqualTo(expected.subResource);
    assertThat(actual.getNamespace()).isEqualTo(expected.namespace);
    assertThat(actual.getName()).isEqualTo(expected.name);
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
            new ParseCase(
                "cluster-scoped list",
                "/apis/apps/v1/deployments",
                new Resource("apps", "v1", "deployments", null, null, null)),
            new ParseCase(
                "namespaced list",
                "/apis/apps/v1/namespaces/default/deployments",
                new Resource("apps", "v1", "deployments", null, "default", null)),
            new ParseCase(
                "namespaced named",
                "/apis/apps/v1/namespaces/default/deployments/foo",
                new Resource("apps", "v1", "deployments", null, "default", "foo")),
            new ParseCase(
                "namespaced named subresource",
                "/apis/apps/v1/namespaces/default/deployments/foo/status",
                new Resource("apps", "v1", "deployments", "status", "default", "foo")),
            new ParseCase(
                "custom resource cluster-scoped list",
                "/apis/example.io/v1alpha1/foos",
                new Resource("example.io", "v1alpha1", "foos", null, null, null)),
            new ParseCase(
                "custom resource namespaced list",
                "/apis/example.io/v1alpha1/namespaces/default/foos",
                new Resource("example.io", "v1alpha1", "foos", null, "default", null)),
            new ParseCase(
                "custom resource namespaced named",
                "/apis/example.io/v1alpha1/namespaces/default/foos/foo",
                new Resource("example.io", "v1alpha1", "foos", null, "default", "foo")),
            new ParseCase(
                "custom resource namespaced named subresource",
                "/apis/example.io/v1alpha1/namespaces/default/foos/foo/status",
                new Resource("example.io", "v1alpha1", "foos", "status", "default", "foo")))
        .map(c -> Arguments.of(c.name, c));
  }

  private static Stream<Arguments> parseCoreResourceArguments() {
    return Stream.of(
            new ParseCase(
                "cluster-scoped list",
                "/api/v1/pods",
                new Resource("", "v1", "pods", null, null, null)),
            new ParseCase(
                "namespaced list",
                "/api/v1/namespaces/default/pods",
                new Resource("", "v1", "pods", null, "default", null)),
            new ParseCase(
                "namespaced named",
                "/api/v1/namespaces/default/pods/foo",
                new Resource("", "v1", "pods", null, "default", "foo")),
            new ParseCase(
                "namespaced named subresource",
                "/api/v1/namespaces/default/pods/foo/exec",
                new Resource("", "v1", "pods", "exec", "default", "foo")))
        .map(c -> Arguments.of(c.name, c));
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

class KubernetesResource {

  public static final Pattern CORE_RESOURCE_URL_PATH_PATTERN =
      Pattern.compile(
          "^/api/v1(/namespaces/(?<namespace>[\\w-]+))?/(?<resource>[\\w-]+)(/(?<name>[\\w-]+))?(/(?<subresource>[\\w-]+))?(/.*)?");

  public static final Pattern REGULAR_RESOURCE_URL_PATH_PATTERN =
      Pattern.compile(
          "^/apis/(?<group>\\S+?)/(?<version>\\S+?)(/namespaces/(?<namespace>[\\w-]+))?/(?<resource>[\\w-]+)(/(?<name>[\\w-]+))?(/(?<subresource>[\\w-]+))?");

  public static KubernetesResource parseCoreResource(String urlPath)
      throws ParseKubernetesResourceException {
    Matcher matcher = CORE_RESOURCE_URL_PATH_PATTERN.matcher(urlPath);
    if (!matcher.matches()) {
      throw new ParseKubernetesResourceException();
    }
    return new KubernetesResource(
        "",
        "v1",
        matcher.group("resource"),
        matcher.group("subresource"),
        matcher.group("namespace"),
        matcher.group("name"));
  }

  public static KubernetesResource parseRegularResource(String urlPath)
      throws ParseKubernetesResourceException {
    Matcher matcher = REGULAR_RESOURCE_URL_PATH_PATTERN.matcher(urlPath);
    if (!matcher.matches()) {
      throw new ParseKubernetesResourceException();
    }
    return new KubernetesResource(
        matcher.group("group"),
        matcher.group("version"),
        matcher.group("resource"),
        matcher.group("subresource"),
        matcher.group("namespace"),
        matcher.group("name"));
  }

  KubernetesResource(
      String apiGroup,
      String apiVersion,
      String resource,
      @Nullable String subResource,
      @Nullable String namespace,
      @Nullable String name) {
    this.apiGroup = apiGroup;
    this.apiVersion = apiVersion;
    this.resource = resource;
    this.subResource = subResource;
    this.namespace = namespace;
    this.name = name;
  }

  private final String apiGroup;
  private final String apiVersion;
  private final String resource;
  @Nullable private final String subResource;

  @Nullable private final String namespace;
  @Nullable private final String name;

  public String getApiGroup() {
    return apiGroup;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public String getResource() {
    return resource;
  }

  @Nullable
  public String getSubResource() {
    return subResource;
  }

  @Nullable
  public String getNamespace() {
    return namespace;
  }

  @Nullable
  public String getName() {
    return name;
  }
}

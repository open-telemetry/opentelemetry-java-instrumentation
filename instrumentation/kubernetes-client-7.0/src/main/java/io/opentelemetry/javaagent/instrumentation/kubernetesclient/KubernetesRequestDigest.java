/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.kubernetesclient;

import com.google.common.base.Strings;
import java.util.regex.Pattern;
import okhttp3.Request;

class KubernetesRequestDigest {

  public static final Pattern RESOURCE_URL_PATH_PATTERN =
      Pattern.compile("^/(api|apis)(/\\S+)?/v\\d\\w*/\\S+");

  KubernetesRequestDigest(
      String urlPath,
      boolean isNonResourceRequest,
      KubernetesResource resourceMeta,
      KubernetesVerb verb) {
    this.urlPath = urlPath;
    this.isNonResourceRequest = isNonResourceRequest;
    this.resourceMeta = resourceMeta;
    this.verb = verb;
  }

  public static KubernetesRequestDigest parse(Request request) {
    String urlPath = request.url().encodedPath();
    if (!isResourceRequest(urlPath)) {
      return nonResource(urlPath);
    }
    try {
      KubernetesResource resourceMeta;
      if (urlPath.startsWith("/api/v1")) {
        resourceMeta = KubernetesResource.parseCoreResource(urlPath);
      } else {
        resourceMeta = KubernetesResource.parseRegularResource(urlPath);
      }

      return new KubernetesRequestDigest(
          urlPath,
          false,
          resourceMeta,
          KubernetesVerb.of(
              request.method(), hasNamePathParameter(resourceMeta), hasWatchParameter(request)));
    } catch (ParseKubernetesResourceException e) {
      return nonResource(urlPath);
    }
  }

  private static KubernetesRequestDigest nonResource(String urlPath) {
    KubernetesRequestDigest digest = new KubernetesRequestDigest(urlPath, true, null, null);
    return digest;
  }

  public static boolean isResourceRequest(String urlPath) {
    return RESOURCE_URL_PATH_PATTERN.matcher(urlPath).matches();
  }

  private static boolean hasWatchParameter(Request request) {
    return !Strings.isNullOrEmpty(request.url().queryParameter("watch"));
  }

  private static boolean hasNamePathParameter(KubernetesResource resource) {
    return !Strings.isNullOrEmpty(resource.getName());
  }

  private final String urlPath;
  private final boolean isNonResourceRequest;

  private final KubernetesResource resourceMeta;
  private final KubernetesVerb verb;

  public String getUrlPath() {
    return urlPath;
  }

  public boolean isNonResourceRequest() {
    return isNonResourceRequest;
  }

  public KubernetesResource getResourceMeta() {
    return resourceMeta;
  }

  public KubernetesVerb getVerb() {
    return verb;
  }

  @Override
  public String toString() {
    if (isNonResourceRequest) {
      return new StringBuilder().append(verb).append(' ').append(urlPath).toString();
    }

    String groupVersion;
    if (Strings.isNullOrEmpty(resourceMeta.getApiGroup())) { // core resource
      groupVersion = "";
    } else { // regular resource
      groupVersion = resourceMeta.getApiGroup() + "/" + resourceMeta.getApiVersion();
    }

    String targetResourceName;
    if (Strings.isNullOrEmpty(resourceMeta.getSubResource())) {
      targetResourceName = resourceMeta.getResource();
    } else { // subresource
      targetResourceName = resourceMeta.getResource() + "/" + resourceMeta.getSubResource();
    }

    return new StringBuilder()
        .append(verb.value())
        .append(' ')
        .append(groupVersion)
        .append(' ')
        .append(targetResourceName)
        .toString();
  }
}

/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.kubernetes;

import java.util.regex.Pattern;
import okhttp3.Request;
import org.apache.commons.lang.StringUtils;

public class KubernetesRequestDigest {

  public static final Pattern RESOURCE_URL_PATH_PATTERN =
      Pattern.compile("^/(api|apis)(/\\S+)?/v\\d\\w*/\\S+");

  private KubernetesRequestDigest(String urlPath, boolean isNonResourceRequest) {
    this.urlPath = urlPath;
    this.isNonResourceRequest = isNonResourceRequest;
  }

  public static KubernetesRequestDigest parse(Request request) {
    String urlPath = request.url().encodedPath();
    if (!isResourceRequest(urlPath)) {
      return nonResource(urlPath);
    }
    try {
      KubernetesRequestDigest digest = new KubernetesRequestDigest(urlPath, false);
      if (StringUtils.startsWith(urlPath, "/api/v1")) {
        digest.resourceMeta = KubernetesResource.parseCoreResource(urlPath);
      } else {
        digest.resourceMeta = KubernetesResource.parseRegularResource(urlPath);
      }
      digest.verb =
          KubernetesVerb.of(
              request.method(),
              hasNamePathParameter(digest.resourceMeta),
              hasWatchParameter(request));
      return digest;
    } catch (ParseKubernetesResourceException e) {
      return nonResource(urlPath);
    }
  }

  private static KubernetesRequestDigest nonResource(String urlPath) {
    KubernetesRequestDigest digest = new KubernetesRequestDigest(urlPath, true);
    return digest;
  }

  public static boolean isResourceRequest(String urlPath) {
    return RESOURCE_URL_PATH_PATTERN.matcher(urlPath).matches();
  }

  private static boolean hasWatchParameter(Request request) {
    return !StringUtils.isEmpty(request.url().queryParameter("watch"));
  }

  private static boolean hasNamePathParameter(KubernetesResource resource) {
    return !StringUtils.isEmpty(resource.getName());
  }

  private final String urlPath;
  private final boolean isNonResourceRequest;

  private KubernetesResource resourceMeta;
  private KubernetesVerb verb;

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
      return String.format("%s %s", verb, urlPath);
    }

    String groupVersion;
    if (StringUtils.isEmpty(resourceMeta.getApiGroup())) { // core resource
      groupVersion = "";
    } else { // regular resource
      groupVersion = resourceMeta.getApiGroup() + "/" + resourceMeta.getApiVersion();
    }

    String targetResourceName;
    if (StringUtils.isEmpty(resourceMeta.getSubResource())) {
      targetResourceName = resourceMeta.getResource();
    } else { // subresource
      targetResourceName = resourceMeta.getResource() + "/" + resourceMeta.getSubResource();
    }

    return String.format("%s %s %s", verb.value(), groupVersion, targetResourceName);
  }
}

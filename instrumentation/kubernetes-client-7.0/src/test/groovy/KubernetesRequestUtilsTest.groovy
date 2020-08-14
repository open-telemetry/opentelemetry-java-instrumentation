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

import io.opentelemetry.instrumentation.auto.kubernetesclient.KubernetesRequestDigest
import io.opentelemetry.instrumentation.auto.kubernetesclient.KubernetesResource
import io.opentelemetry.instrumentation.auto.kubernetesclient.KubernetesVerb
import spock.lang.Specification

class KubernetesRequestUtilsTest extends Specification {
  def "asserting non-resource requests should work"() {
    expect:
    !KubernetesRequestDigest.isResourceRequest("/api")
    !KubernetesRequestDigest.isResourceRequest("/apis")
    !KubernetesRequestDigest.isResourceRequest("/apis/v1")
    !KubernetesRequestDigest.isResourceRequest("/healthz")
    !KubernetesRequestDigest.isResourceRequest("/swagger.json")
    !KubernetesRequestDigest.isResourceRequest("/api/v1")
    !KubernetesRequestDigest.isResourceRequest("/api/v1/")
    !KubernetesRequestDigest.isResourceRequest("/apis/apps/v1")
    !KubernetesRequestDigest.isResourceRequest("/apis/apps/v1/")
  }

  def "asserting resource requests should work"() {
    expect:
    KubernetesRequestDigest.isResourceRequest("/apis/example.io/v1/foos")
    KubernetesRequestDigest.isResourceRequest("/apis/example.io/v1/namespaces/default/foos")
    KubernetesRequestDigest.isResourceRequest("/api/v1/namespaces")
    KubernetesRequestDigest.isResourceRequest("/api/v1/pods")
    KubernetesRequestDigest.isResourceRequest("/api/v1/namespaces/default/pods")
  }

  def "parsing core resource from url-path should work"(String urlPath, String apiGroup, String apiVersion, String resource, String subResource, String namespace, String name) {
    expect:
    KubernetesResource.parseCoreResource(urlPath).apiGroup == apiGroup
    KubernetesResource.parseCoreResource(urlPath).apiVersion == apiVersion
    KubernetesResource.parseCoreResource(urlPath).resource == resource
    KubernetesResource.parseCoreResource(urlPath).subResource == subResource
    KubernetesResource.parseCoreResource(urlPath).namespace == namespace
    KubernetesResource.parseCoreResource(urlPath).name == name

    where:
    urlPath                                    | apiGroup | apiVersion | resource | subResource | namespace | name
    "/api/v1/pods"                             | ""       | "v1"       | "pods"   | null        | null      | null
    "/api/v1/namespaces/default/pods"          | ""       | "v1"       | "pods"   | null        | "default" | null
    "/api/v1/namespaces/default/pods/foo"      | ""       | "v1"       | "pods"   | null        | "default" | "foo"
    "/api/v1/namespaces/default/pods/foo/exec" | ""       | "v1"       | "pods"   | "exec"      | "default" | "foo"
  }

  def "parsing regular non-core resource from url-path should work"(String urlPath, String apiGroup, String apiVersion, String resource, String subResource, String namespace, String name) {
    expect:
    KubernetesResource.parseRegularResource(urlPath).apiGroup == apiGroup
    KubernetesResource.parseRegularResource(urlPath).apiVersion == apiVersion
    KubernetesResource.parseRegularResource(urlPath).resource == resource
    KubernetesResource.parseRegularResource(urlPath).subResource == subResource
    KubernetesResource.parseRegularResource(urlPath).namespace == namespace
    KubernetesResource.parseRegularResource(urlPath).name == name

    where:
    urlPath                                                        | apiGroup     | apiVersion | resource      | subResource | namespace | name
    "/apis/apps/v1/deployments"                                    | "apps"       | "v1"       | "deployments" | null        | null      | null
    "/apis/apps/v1/namespaces/default/deployments"                 | "apps"       | "v1"       | "deployments" | null        | "default" | null
    "/apis/apps/v1/namespaces/default/deployments/foo"             | "apps"       | "v1"       | "deployments" | null        | "default" | "foo"
    "/apis/apps/v1/namespaces/default/deployments/foo/status"      | "apps"       | "v1"       | "deployments" | "status"    | "default" | "foo"
    "/apis/example.io/v1alpha1/foos"                               | "example.io" | "v1alpha1" | "foos"        | null        | null      | null
    "/apis/example.io/v1alpha1/namespaces/default/foos"            | "example.io" | "v1alpha1" | "foos"        | null        | "default" | null
    "/apis/example.io/v1alpha1/namespaces/default/foos/foo"        | "example.io" | "v1alpha1" | "foos"        | null        | "default" | "foo"
    "/apis/example.io/v1alpha1/namespaces/default/foos/foo/status" | "example.io" | "v1alpha1" | "foos"        | "status"    | "default" | "foo"
  }

  def "parsing kubernetes request verbs should work"(String httpVerb, boolean hasNamePathParam, boolean hasWatchParam, KubernetesVerb kubernetesVerb) {
    expect:
    KubernetesVerb.of(httpVerb, hasNamePathParam, hasWatchParam) == kubernetesVerb

    where:
    httpVerb | hasNamePathParam | hasWatchParam | kubernetesVerb
    "GET"    | true             | false         | KubernetesVerb.GET
    "GET"    | false            | true          | KubernetesVerb.WATCH
    "GET"    | false            | false         | KubernetesVerb.LIST
    "POST"   | false            | false         | KubernetesVerb.CREATE
    "PUT"    | false            | false         | KubernetesVerb.UPDATE
    "PATCH"  | false            | false         | KubernetesVerb.PATCH
    "DELETE" | true             | false         | KubernetesVerb.DELETE
    "DELETE" | false            | false         | KubernetesVerb.DELETE_COLLECTION
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import feign.MethodMetadata;
import feign.RequestTemplate;
import feign.Target;
import java.net.URI;

public class ExecuteAndDecodeRequest {

  private final Target<?> target;
  private final MethodMetadata metadata;
  private final RequestTemplate requestTemplate;
  private final URI uri;

  public ExecuteAndDecodeRequest(Target<?> target, MethodMetadata metadata,
      RequestTemplate requestTemplate) {
    this.target = target;
    this.metadata = metadata;
    this.requestTemplate = requestTemplate;
    uri = URI.create(requestTemplate.url());
  }

  public URI getTemplateUri() {
    return uri;
  }

  public RequestTemplate getRequestTemplate() {
    return requestTemplate;
  }

  public Target<?> getTarget() {
    return target;
  }

  public MethodMetadata getMetadata() {
    return metadata;
  }
}

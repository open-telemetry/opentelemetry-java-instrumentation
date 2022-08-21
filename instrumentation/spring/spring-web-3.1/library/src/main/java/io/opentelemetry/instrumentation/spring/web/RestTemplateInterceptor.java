/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;

import io.opentelemetry.instrumentation.api.instrumenter.peer.PeerResourceAttributes;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

final class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

  private final Instrumenter<HttpRequest, ClientHttpResponse> instrumenter;
  
  private final Attributes resourceAttributes;
  
  RestTemplateInterceptor(Instrumenter<HttpRequest, ClientHttpResponse> instrumenter, Attributes resourceAttributes) {
    this.instrumenter = instrumenter;
    this.resourceAttributes = resourceAttributes;
  }

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    Context parentContext = Context.current();
    if (request.getHeaders() != null){
  
      String peerServiceName = resourceAttributes.get(ResourceAttributes.SERVICE_NAME);
      if (peerServiceName != null) {
        request.getHeaders().set(PeerResourceAttributes.PEER_SERVICE, peerServiceName);
      }
  
      String peerServiceNamespace = resourceAttributes.get(ResourceAttributes.SERVICE_NAMESPACE);
      if (peerServiceName != null) {
        request.getHeaders().set(PeerResourceAttributes.PEER_SERVICE_NAMESPACE, peerServiceNamespace);
      }
      
      String deploymentEnvironment = resourceAttributes.get(ResourceAttributes.DEPLOYMENT_ENVIRONMENT);
      if (peerServiceName != null) {
        request.getHeaders().set(PeerResourceAttributes.PEER_DEPLOYMENT_ENVIRONMENT, deploymentEnvironment);
      }
      
      String peerServiceVersion = resourceAttributes.get(ResourceAttributes.SERVICE_VERSION);
      if (peerServiceVersion != null) {
        request.getHeaders().set(PeerResourceAttributes.PEER_SERVICE_VERSION, peerServiceVersion);
      }
      
      String peerServiceInstanceID = resourceAttributes.get(ResourceAttributes.SERVICE_INSTANCE_ID);
      if (peerServiceInstanceID != null) {
        request.getHeaders().set(PeerResourceAttributes.PEER_SERVICE_INSTANCE_ID, peerServiceInstanceID);
      }
    }
    
    if (!instrumenter.shouldStart(parentContext, request)) {
      return execution.execute(request, body);
    }
    
    Context context = instrumenter.start(parentContext, request);
    try (Scope ignored = context.makeCurrent()) {
      ClientHttpResponse response = execution.execute(request, body);
      instrumenter.end(context, request, response, null);
      return response;
    } catch (Throwable t) {
      instrumenter.end(context, request, null, t);
      throw t;
    }
  }
}

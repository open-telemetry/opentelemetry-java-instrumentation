/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Apache Camel Opentracing Component
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import io.opentelemetry.javaagent.instrumentation.apachecamel.CamelDirection;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RestSpanDecorator extends HttpSpanDecorator {

  private static final Logger LOG = LoggerFactory.getLogger(RestSpanDecorator.class);

  protected static String getPath(String uri) {
    // Obtain the 'path' part of the URI format: rest://method:path[:uriTemplate]?[options]
    String path = null;
    int index = uri.indexOf(':');
    if (index != -1) {
      index = uri.indexOf(':', index + 1);
      if (index != -1) {
        path = uri.substring(index + 1);
        index = path.indexOf('?');
        if (index != -1) {
          path = path.substring(0, index);
        }
        path = path.replaceAll(":", "");
        try {
          path = URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
          LOG.debug("Failed to decode URL path '" + path + "', ignoring exception", e);
        }
      }
    }
    return path;
  }

  @Override
  public String getOperationName(
      Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {
    return (CamelDirection.INBOUND.equals(camelDirection)
        ? getPath(endpoint.getEndpointUri())
        : getHttpMethod(exchange, endpoint));
  }
}

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
package io.opentelemetry.auto.typed.http.delegate;

import io.opentelemetry.trace.Span;

public interface HttpServerSemanticConvention {
  void end();

  Span getSpan();

  HttpServerSemanticConvention setNetHostPort(long netHostPort);

  HttpServerSemanticConvention setNetHostName(String netHostName);

  HttpServerSemanticConvention setServerName(String server_name);

  HttpServerSemanticConvention setRoute(String route);

  HttpServerSemanticConvention setClientIp(String client_ip);

  HttpServerSemanticConvention setMethod(String method);

  HttpServerSemanticConvention setUrl(String url);

  HttpServerSemanticConvention setTarget(String target);

  HttpServerSemanticConvention setHost(String host);

  HttpServerSemanticConvention setScheme(String scheme);

  HttpServerSemanticConvention setStatusCode(long status_code);

  HttpServerSemanticConvention setStatusText(String status_text);

  HttpServerSemanticConvention setFlavor(String flavor);

  HttpServerSemanticConvention setUserAgent(String user_agent);
}

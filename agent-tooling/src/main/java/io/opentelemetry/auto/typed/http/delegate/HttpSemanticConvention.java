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

public interface HttpSemanticConvention {
  void end();

  Span getSpan();

  HttpSemanticConvention setMethod(String method);

  HttpSemanticConvention setUrl(String url);

  HttpSemanticConvention setTarget(String target);

  HttpSemanticConvention setHost(String host);

  HttpSemanticConvention setScheme(String scheme);

  HttpSemanticConvention setStatusCode(long status_code);

  HttpSemanticConvention setStatusText(String status_text);

  HttpSemanticConvention setFlavor(String flavor);

  HttpSemanticConvention setUserAgent(String user_agent);
}

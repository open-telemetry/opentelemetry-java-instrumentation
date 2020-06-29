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

package io.opentelemetry.auto.bootstrap.instrumentation.decorator;

import io.opentelemetry.trace.Status;

public final class HttpUtil {

  // https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/http.md#status
  public static Status statusFromHttpStatus(int httpStatus) {
    if (httpStatus >= 100 && httpStatus < 400) {
      return Status.OK;
    }

    switch (httpStatus) {
      case 401:
        return Status.UNAUTHENTICATED;
      case 403:
        return Status.PERMISSION_DENIED;
      case 404:
        return Status.NOT_FOUND;
      case 429:
        return Status.RESOURCE_EXHAUSTED;
      case 501:
        return Status.UNIMPLEMENTED;
      case 503:
        return Status.UNAVAILABLE;
      case 504:
        return Status.DEADLINE_EXCEEDED;
      default:
        // fall through
    }

    if (httpStatus >= 400 && httpStatus < 500) {
      return Status.INVALID_ARGUMENT;
    }

    if (httpStatus >= 500 && httpStatus < 600) {
      return Status.INTERNAL;
    }

    return Status.UNKNOWN;
  }

  private HttpUtil() {}
}

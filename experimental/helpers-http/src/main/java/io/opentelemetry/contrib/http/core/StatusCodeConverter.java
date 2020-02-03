/*
 * Copyright 2020, OpenTelemetry Authors
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

package io.opentelemetry.contrib.http.core;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.trace.Status;
import java.util.Map;
import javax.annotation.Nullable;

/** Provides standard translation from HTTP status codes to Open Telemetry trace status values. */
public class StatusCodeConverter {

  private static final Map<Integer, Status> STATUS_MAP;

  static {
    ImmutableMap.Builder<Integer, Status> builder = ImmutableMap.builder();
    builder.put(400, Status.INVALID_ARGUMENT);
    builder.put(401, Status.UNAUTHENTICATED);
    builder.put(403, Status.PERMISSION_DENIED);
    builder.put(404, Status.NOT_FOUND);
    builder.put(429, Status.RESOURCE_EXHAUSTED);
    builder.put(500, Status.INTERNAL);
    builder.put(501, Status.UNIMPLEMENTED);
    builder.put(503, Status.UNAVAILABLE);
    builder.put(504, Status.DEADLINE_EXCEEDED);
    STATUS_MAP = builder.build();
  }

  /**
   * Translates the supplied HTTP status code to equivalent Open Telemetry status.
   *
   * @param httpStatus the source value
   * @return the target value
   */
  public Status convert(int httpStatus) {
    Status status = doCustomConvert(httpStatus);
    if (status != null) {
      return status;
    }
    if (httpStatus >= 200 && httpStatus < 400) {
      return Status.OK;
    }
    status = STATUS_MAP.get(httpStatus);
    if (status != null) {
      return status;
    }
    if (httpStatus >= 400 && httpStatus < 500) {
      status = Status.INVALID_ARGUMENT;
    } else if (httpStatus >= 500 && httpStatus < 600) {
      status = Status.INTERNAL;
    } else {
      status = Status.UNKNOWN;
    }
    return status;
  }

  /**
   * Provides non-standard HTTP status mappings if this method is overridden in a sub-class.
   *
   * @param httpStatus the source value
   * @return the target value
   */
  @Nullable
  protected Status doCustomConvert(int httpStatus) {
    return null; // NoOp
  }
}

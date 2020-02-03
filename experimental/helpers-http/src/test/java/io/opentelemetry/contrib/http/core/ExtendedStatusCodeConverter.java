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

import io.opentelemetry.trace.Status;
import javax.annotation.Nullable;

/**
 * Tests extension of standard status conversion to get custom behavior for only a few HTTP status
 * codes.
 */
public class ExtendedStatusCodeConverter extends StatusCodeConverter {

  @Nullable
  @Override
  protected Status doCustomConvert(int httpStatus) {
    if (httpStatus == 409) {
      return Status.ALREADY_EXISTS;
    } else {
      return null;
    }
  }
}

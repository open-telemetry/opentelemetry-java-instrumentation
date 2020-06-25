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

package rx;

/**
 * This class must be in the rx package in order to access the package accessible onSubscribe field.
 */
public class __OpenTelemetryTracingUtil {
  public static <T> Observable.OnSubscribe<T> extractOnSubscribe(final Observable<T> observable) {
    return observable.onSubscribe;
  }
}

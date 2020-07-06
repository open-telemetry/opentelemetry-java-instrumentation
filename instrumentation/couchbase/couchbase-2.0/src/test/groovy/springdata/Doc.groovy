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

package springdata

import groovy.transform.EqualsAndHashCode
import org.springframework.data.annotation.Id
import org.springframework.data.couchbase.core.mapping.Document

@Document
@EqualsAndHashCode
class Doc {
  @Id
  private String id = "1"
  private String data = "some data"

  String getId() {
    return id
  }

  void setId(String id) {
    this.id = id
  }

  String getData() {
    return data
  }

  void setData(String data) {
    this.data = data
  }
}

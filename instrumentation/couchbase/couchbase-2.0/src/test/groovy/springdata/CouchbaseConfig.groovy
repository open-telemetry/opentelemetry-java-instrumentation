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

import com.couchbase.client.java.cluster.BucketSettings
import com.couchbase.client.java.env.CouchbaseEnvironment
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories

import static com.google.common.base.Preconditions.checkNotNull

@Configuration
@EnableCouchbaseRepositories(basePackages = "springdata")
@ComponentScan(basePackages = "springdata")
class CouchbaseConfig extends AbstractCouchbaseConfiguration {

  // These need to be set before this class can be used by Spring
  static CouchbaseEnvironment environment
  static BucketSettings bucketSettings

  @Override
  protected CouchbaseEnvironment getEnvironment() {
    return checkNotNull(environment)
  }

  @Override
  protected List<String> getBootstrapHosts() {
    return Collections.singletonList("127.0.0.1")
  }

  @Override
  protected String getBucketName() {
    return bucketSettings.name()
  }

  @Override
  protected String getBucketPassword() {
    return bucketSettings.password()
  }

}

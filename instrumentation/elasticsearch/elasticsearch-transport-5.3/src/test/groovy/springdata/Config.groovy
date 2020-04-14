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

import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.env.Environment
import org.elasticsearch.node.InternalSettingsPreparer
import org.elasticsearch.node.Node
import org.elasticsearch.transport.Netty3Plugin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories

@Configuration
@EnableElasticsearchRepositories(basePackages = "springdata")
@ComponentScan(basePackages = "springdata")
class Config {

  @Bean
  NodeBuilder nodeBuilder() {
    return new NodeBuilder()
  }

  @Bean
  ElasticsearchOperations elasticsearchTemplate() {
    def tmpDir = File.createTempFile("test-es-working-dir-", "")
    tmpDir.delete()
    tmpDir.mkdir()
    tmpDir.deleteOnExit()

    System.addShutdownHook {
      if (tmpDir != null) {
        FileSystemUtils.deleteSubDirectories(tmpDir.toPath())
        tmpDir.delete()
      }
    }

    def settings = Settings.builder()
      .put("http.enabled", "false")
      .put("path.data", tmpDir.toString())
      .put("path.home", tmpDir.toString())
      .put("thread_pool.listener.size", 1)
      .put("transport.type", "netty3")
      .put("http.type", "netty3")
      .build()

    println "ES work dir: $tmpDir"

    return new ElasticsearchTemplate(new Node(new Environment(InternalSettingsPreparer.prepareSettings(settings)), [Netty3Plugin]).start().client())
  }
}

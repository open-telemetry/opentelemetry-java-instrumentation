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
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.IMongodConfig
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import spock.lang.Shared

/**
 * Testing needs to be in a centralized project.
 * If tests in multiple different projects are using embedded mongo,
 * they downloader is at risk of a race condition.
 */
class MongoBaseTest extends AgentTestRunner {
  // https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo#executable-collision
  private static final MongodStarter STARTER = MongodStarter.getDefaultInstance()

  @Shared
  int port = PortUtils.randomOpenPort()
  @Shared
  MongodExecutable mongodExe
  @Shared
  MongodProcess mongod

  def setup() throws Exception {
    final IMongodConfig mongodConfig =
      new MongodConfigBuilder()
        .version(Version.Main.PRODUCTION)
        .net(new Net("localhost", port, Network.localhostIsIPv6()))
        .build()

    // using a system-wide file lock to prevent other modules that may be running in parallel
    // from clobbering each other while downloading and extracting mongodb
    def lockFile = new File(System.getProperty("java.io.tmpdir"), "prepare-embedded-mongo.lock")
    def channel = new RandomAccessFile(lockFile, "rw").getChannel()
    def lock = channel.lock()
    try {
      mongodExe = STARTER.prepare(mongodConfig)
    } finally {
      lock.release()
      channel.close()
    }
    mongod = mongodExe.start()
  }

  def cleanup() throws Exception {
    mongod?.stop()
    mongod = null
    mongodExe?.stop()
    mongodExe = null
  }

  def "test port open"() {
    when:
    new Socket("localhost", port)

    then:
    noExceptionThrown()
  }
}

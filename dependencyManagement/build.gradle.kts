import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
  `java-platform`

  id("com.github.ben-manes.versions")
}

data class DependencySet(val group: String, val version: String, val modules: List<String>)

val dependencyVersions = hashMapOf<String, String>()
rootProject.extra["versions"] = dependencyVersions

val otelVersion = "1.9.1"
rootProject.extra["otelVersion"] = otelVersion

// Need both BOM and -all
val groovyVersion = "2.5.14"

rootProject.extra["caffeine2Version"] = "2.9.2"
rootProject.extra["caffeine3Version"] = "3.0.4"

// We don't force libraries we instrument to new versions since we compile and test against specific
// old baseline versions
// but we do try to force those libraries' transitive dependencies to new versions where possible
// so that we don't end up with explosion of dependency versions in Intellij, which causes
// Intellij to spend lots of time indexing all of those different dependency versions,
// and makes debugging painful because Intellij has no idea which dependency version's source
// to use when stepping through code.
//
// Sometimes libraries we instrument do require a specific version of a transitive dependency
// and that can be applied in the specific instrumentation gradle file, e.g.
// configurations.testRuntimeClasspath.resolutionStrategy.force "com.google.guava:guava:19.0"

val DEPENDENCY_BOMS = listOf(
  "com.fasterxml.jackson:jackson-bom:2.12.3",
  "com.google.guava:guava-bom:30.1.1-jre",
  "org.codehaus.groovy:groovy-bom:${groovyVersion}",
  "io.opentelemetry:opentelemetry-bom:${otelVersion}",
  "io.opentelemetry:opentelemetry-bom-alpha:${otelVersion}-alpha",
  "org.junit:junit-bom:5.7.2"
)

val DEPENDENCY_SETS = listOf(
  DependencySet(
    "com.google.auto.value",
    "1.8.1",
    listOf("auto-value", "auto-value-annotations")
  ),
  DependencySet(
    "com.google.errorprone",
    "2.7.1",
    listOf("error_prone_annotations", "error_prone_core")
  ),
  DependencySet(
    "io.prometheus",
    "0.12.0",
    listOf("simpleclient", "simpleclient_common", "simpleclient_httpserver")
  ),
  DependencySet(
    "net.bytebuddy",
    // When updating, also update conventions/build.gradle.kts
    "1.11.22",
    listOf("byte-buddy", "byte-buddy-dep", "byte-buddy-agent", "byte-buddy-gradle-plugin")
  ),
  DependencySet(
    "org.openjdk.jmh",
    "1.32",
    listOf("jmh-core", "jmh-generator-bytecode")
  ),
  DependencySet(
    "org.mockito",
    "3.11.1",
    listOf("mockito-core", "mockito-junit-jupiter")
  ),
  DependencySet(
    "org.slf4j",
    "1.7.30",
    listOf("slf4j-api", "slf4j-simple", "log4j-over-slf4j", "jcl-over-slf4j", "jul-to-slf4j")
  ),
  DependencySet(
    "org.testcontainers",
    "1.15.3",
    listOf("testcontainers", "junit-jupiter", "cassandra", "couchbase", "elasticsearch", "kafka", "localstack", "selenium")
  )
)

val DEPENDENCIES = listOf(
  "ch.qos.logback:logback-classic:1.2.3",
  "com.blogspot.mydailyjava:weak-lock-free:0.18",
  "com.github.stefanbirkner:system-lambda:1.2.0",
  "com.github.stefanbirkner:system-rules:1.19.0",
  "com.google.auto.service:auto-service:1.0",
  "com.uber.nullaway:nullaway:0.9.1",
  "commons-beanutils:commons-beanutils:1.9.4",
  "commons-cli:commons-cli:1.4",
  "commons-codec:commons-codec:1.15",
  "commons-collections:commons-collections:3.2.2",
  "commons-digester:commons-digester:2.1",
  "commons-fileupload:commons-fileupload:1.4",
  "commons-io:commons-io:2.10.0",
  "commons-lang:commons-lang:2.6",
  "commons-logging:commons-logging:1.2",
  "commons-validator:commons-validator:1.7",
  "io.netty:netty:3.10.6.Final",
  "io.opentelemetry.proto:opentelemetry-proto:0.11.0-alpha",
  "org.assertj:assertj-core:3.21.0",
  "org.awaitility:awaitility:4.1.0",
  "com.google.code.findbugs:jsr305:3.0.2",
  "org.codehaus.groovy:groovy-all:${groovyVersion}",
  "org.objenesis:objenesis:3.2",
  "org.spockframework:spock-core:2.0-groovy-2.5",
  "org.spockframework:spock-junit4:2.0-groovy-2.5",
  "org.scala-lang:scala-library:2.11.12",
  "org.springframework.boot:spring-boot-dependencies:2.3.1.RELEASE",
)

javaPlatform {
  allowDependencies()
}

dependencies {
  for (bom in DEPENDENCY_BOMS) {
    api(enforcedPlatform(bom))
    val split = bom.split(':')
    dependencyVersions[split[0]] = split[2]
  }
  constraints {
    for (set in DEPENDENCY_SETS) {
      for (module in set.modules) {
        api("${set.group}:${module}:${set.version}")
        dependencyVersions[set.group] = set.version
      }
    }
    for (dependency in DEPENDENCIES) {
      api(dependency)
      val split = dependency.split(':')
      dependencyVersions[split[0]] = split[2]
    }
  }
}

fun isNonStable(version: String): Boolean {
  val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
  val regex = "^[0-9,.v-]+(-r)?$".toRegex()
  val isGuava = version.endsWith("-jre")
  val isStable = stableKeyword || regex.matches(version) || isGuava
  return isStable.not()
}

tasks {
  named<DependencyUpdatesTask>("dependencyUpdates") {
    revision = "release"
    checkConstraints = true

    rejectVersionIf {
      isNonStable(candidate.version)
    }
  }
}

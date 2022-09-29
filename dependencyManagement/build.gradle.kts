import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
  `java-platform`

  id("com.github.ben-manes.versions")
}

data class DependencySet(val group: String, val version: String, val modules: List<String>)

val dependencyVersions = hashMapOf<String, String>()
rootProject.extra["versions"] = dependencyVersions

// this line is managed by .github/scripts/update-sdk-version.sh
val otelVersion = "1.18.0"

rootProject.extra["otelVersion"] = otelVersion

// Need both BOM and groovy jars
val groovyVersion = "4.0.5"

// We don't force libraries we instrument to new versions since we compile and test against specific
// old baseline versions but we do try to force those libraries' transitive dependencies to new
// versions where possible so that we don't end up with explosion of dependency versions in
// Intellij, which causes Intellij to spend lots of time indexing all of those different dependency
// versions, and makes debugging painful because Intellij has no idea which dependency version's
// source to use when stepping through code.
//
// Sometimes libraries we instrument do require a specific version of a transitive dependency and
// that can be applied in the specific instrumentation gradle file, e.g.
// configurations.testRuntimeClasspath.resolutionStrategy.force "com.google.guava:guava:19.0"

val DEPENDENCY_BOMS = listOf(
  "com.fasterxml.jackson:jackson-bom:2.13.4",
  "com.google.guava:guava-bom:31.1-jre",
  "org.apache.groovy:groovy-bom:${groovyVersion}",
  "io.opentelemetry:opentelemetry-bom:${otelVersion}",
  "io.opentelemetry:opentelemetry-bom-alpha:${otelVersion}-alpha",
  "org.junit:junit-bom:5.9.1",
  "org.testcontainers:testcontainers-bom:1.17.3",
)

val CORE_DEPENDENCIES = listOf(
  "com.google.auto.service:auto-service:1.0.1",
  "com.google.auto.service:auto-service-annotations:1.0.1",
  "com.google.auto.value:auto-value:1.9",
  "com.google.auto.value:auto-value-annotations:1.9",
  "com.google.errorprone:error_prone_annotations:2.15.0",
  "com.google.errorprone:error_prone_core:2.15.0",
  "com.google.errorprone:error_prone_test_helpers:2.15.0",
  // When updating, also update conventions/build.gradle.kts
  "net.bytebuddy:byte-buddy:1.12.17",
  "net.bytebuddy:byte-buddy-dep:1.12.17",
  "net.bytebuddy:byte-buddy-agent:1.12.17",
  "net.bytebuddy:byte-buddy-gradle-plugin:1.12.17",
  "org.openjdk.jmh:jmh-core:1.35",
  "org.openjdk.jmh:jmh-generator-bytecode:1.35",
  "org.mockito:mockito-core:4.8.0",
  "org.mockito:mockito-junit-jupiter:4.8.0",
  "org.mockito:mockito-inline:4.8.0",
  "org.slf4j:slf4j-api:1.7.36",
  "org.slf4j:slf4j-simple:1.7.36",
  "org.slf4j:log4j-over-slf4j:1.7.36",
  "org.slf4j:jcl-over-slf4j:1.7.36",
  "org.slf4j:jul-to-slf4j:1.7.36"
)

// See the comment above about why we keep this rather large list.
// There are dependencies included here that appear to have no usages, but are maintained at
// this top level to help consistently satisfy large numbers of transitive dependencies.
val DEPENDENCIES = listOf(
  "ch.qos.logback:logback-classic:1.2.11",
  "com.github.stefanbirkner:system-lambda:1.2.1",
  "com.github.stefanbirkner:system-rules:1.19.0",
  "uk.org.webcompere:system-stubs-jupiter:2.0.1",
  "com.uber.nullaway:nullaway:0.10.2",
  "commons-beanutils:commons-beanutils:1.9.4",
  "commons-cli:commons-cli:1.5.0",
  "commons-codec:commons-codec:1.15",
  "commons-collections:commons-collections:3.2.2",
  "commons-digester:commons-digester:2.1",
  "commons-fileupload:commons-fileupload:1.4",
  "commons-io:commons-io:2.11.0",
  "commons-lang:commons-lang:2.6",
  "commons-logging:commons-logging:1.2",
  "commons-validator:commons-validator:1.7",
  "io.netty:netty:3.10.6.Final",
  "io.opentelemetry.proto:opentelemetry-proto:0.19.0-alpha",
  "org.assertj:assertj-core:3.23.1",
  "org.awaitility:awaitility:4.2.0",
  "com.google.code.findbugs:annotations:3.0.1u2",
  "com.google.code.findbugs:jsr305:3.0.2",
  "org.apache.groovy:groovy:${groovyVersion}",
  "org.apache.groovy:groovy-json:${groovyVersion}",
  "org.codehaus.mojo:animal-sniffer-annotations:1.22",
  "org.junit-pioneer:junit-pioneer:1.7.1",
  "org.objenesis:objenesis:3.3",
  "org.spockframework:spock-core:2.2-groovy-4.0",
  "org.spockframework:spock-junit4:2.3-groovy-4.0",
  "org.spockframework:spock-spring:2.2-groovy-4.0",
  "org.scala-lang:scala-library:2.11.12",
  // Note that this is only referenced as "org.springframework.boot" in build files, not the artifact name.
  "org.springframework.boot:spring-boot-dependencies:2.7.4",
  "javax.validation:validation-api:2.0.1.Final",
  "org.yaml:snakeyaml:1.33"
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
    for (dependency in CORE_DEPENDENCIES) {
      api(dependency)
      val split = dependency.split(':')
      dependencyVersions[split[0]] = split[2]
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

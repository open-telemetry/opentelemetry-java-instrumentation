plugins {
  `kotlin-dsl`
  // When updating, update below in dependencies too
  id("com.diffplug.spotless") version "6.11.0"
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("../buildscripts/spotless.license.java"), "(package|import|public)")
    target("src/**/*.java")
  }
  kotlinGradle {
    // not sure why it's not using the indent settings from .editorconfig
    ktlint().editorConfigOverride(mapOf("indent_size" to "2", "continuation_indent_size" to "2", "disabled_rules" to "no-wildcard-imports"))
    target("**/*.gradle.kts")
  }
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

dependencies {
  implementation(gradleApi())
  implementation(localGroovy())

  // dependencySubstitution is applied to this dependency (see seetings.gradle.kts)
  implementation("io.opentelemetry.instrumentation:gradle-plugins")

  implementation("org.eclipse.aether:aether-connector-basic:1.1.0")
  implementation("org.eclipse.aether:aether-transport-http:1.1.0")
  implementation("org.apache.maven:maven-aether-provider:3.3.9")

  // When updating, update above in plugins too
  implementation("com.diffplug.spotless:spotless-plugin-gradle:6.11.0")
  implementation("com.google.guava:guava:31.1-jre")
  implementation("gradle.plugin.com.google.protobuf:protobuf-gradle-plugin:0.8.18")
  implementation("gradle.plugin.com.github.johnrengelman:shadow:7.1.2")
  implementation("org.ow2.asm:asm:9.4")
  implementation("org.ow2.asm:asm-tree:9.4")
  implementation("org.apache.httpcomponents:httpclient:4.5.13")
  implementation("org.gradle:test-retry-gradle-plugin:1.4.1")
  implementation("org.owasp:dependency-check-gradle:7.3.0")
  implementation("ru.vyarus:gradle-animalsniffer-plugin:1.6.0")
  // When updating, also update dependencyManagement/build.gradle.kts
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.12.19")
  implementation("gradle.plugin.io.morethan.jmhreport:gradle-jmh-report:0.9.0")
  implementation("me.champeau.jmh:jmh-gradle-plugin:0.6.8")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:3.0.1")
  implementation("net.ltgt.gradle:gradle-nullaway-plugin:1.5.0")
  implementation("me.champeau.gradle:japicmp-gradle-plugin:0.4.1")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.9.1"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testImplementation("org.assertj:assertj-core:3.23.1")
}

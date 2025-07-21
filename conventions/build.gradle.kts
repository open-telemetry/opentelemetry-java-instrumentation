plugins {
  `kotlin-dsl`
  // When updating, update below in dependencies too
  id("com.diffplug.spotless") version "7.2.0"
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(
      rootProject.file("../buildscripts/spotless.license.java"),
      "(package|import|public)"
    )
    target("src/**/*.java")
  }
  kotlinGradle {
    // not sure why it's not using the indent settings from .editorconfig
    ktlint().editorConfigOverride(mapOf(
      "indent_size" to "2",
      "continuation_indent_size" to "2",
      "max_line_length" to "160",
      "ktlint_standard_no-wildcard-imports" to "disabled",
      // ktlint does not break up long lines, it just fails on them
      "ktlint_standard_max-line-length" to "disabled",
      // ktlint makes it *very* hard to locate where this actually happened
      "ktlint_standard_trailing-comma-on-call-site" to "disabled",
      // depends on ktlint_standard_wrapping
      "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
      // also very hard to find out where this happens
      "ktlint_standard_wrapping" to "disabled"
    ))
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
  implementation("com.diffplug.spotless:spotless-plugin-gradle:7.2.0")
  implementation("com.google.guava:guava:33.4.8-jre")
  implementation("gradle.plugin.com.google.protobuf:protobuf-gradle-plugin:0.8.18")
  implementation("com.gradleup.shadow:shadow-gradle-plugin:8.3.8")
  implementation("org.apache.httpcomponents:httpclient:4.5.14")
  implementation("com.gradle.develocity:com.gradle.develocity.gradle.plugin:4.1")
  implementation("org.owasp:dependency-check-gradle:12.1.3")
  implementation("ru.vyarus:gradle-animalsniffer-plugin:2.0.1")
  implementation("org.spdx:spdx-gradle-plugin:0.9.0")
  // When updating, also update dependencyManagement/build.gradle.kts
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.17.6")
  implementation("gradle.plugin.io.morethan.jmhreport:gradle-jmh-report:0.9.6")
  implementation("me.champeau.jmh:jmh-gradle-plugin:0.7.3")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.3.0")
  implementation("net.ltgt.gradle:gradle-nullaway-plugin:2.2.0")
  implementation("me.champeau.gradle:japicmp-gradle-plugin:0.4.6")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.13.3"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testImplementation("org.assertj:assertj-core:3.27.3")
}

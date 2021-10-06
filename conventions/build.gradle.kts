plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
  // When updating, update below in dependencies too
  id("com.diffplug.spotless") version "5.16.0"
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("../buildscripts/spotless.license.java"), "(package|import|public)")
    target("src/**/*.java")
  }
}

repositories {
  mavenCentral()
  gradlePluginPortal()
  maven {
    url = uri("https://oss.sonatype.org/content/repositories/snapshots")
  }
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

dependencies {
  implementation(gradleApi())
  implementation(localGroovy())

  implementation("io.opentelemetry.instrumentation.muzzle-generation:io.opentelemetry.instrumentation.muzzle-generation.gradle.plugin:0.8.0-SNAPSHOT")
  implementation("io.opentelemetry.instrumentation.muzzle-check:io.opentelemetry.instrumentation.muzzle-check.gradle.plugin:0.8.0-SNAPSHOT")

  implementation("org.eclipse.aether:aether-connector-basic:1.1.0")
  implementation("org.eclipse.aether:aether-transport-http:1.1.0")
  implementation("org.apache.maven:maven-aether-provider:3.3.9")

  // When updating, update above in plugins too
  implementation("com.diffplug.spotless:spotless-plugin-gradle:5.16.0")
  implementation("com.google.guava:guava:30.1-jre")
  implementation("gradle.plugin.com.github.jengelman.gradle.plugins:shadow:7.0.0")
  implementation("org.ow2.asm:asm:9.1")
  implementation("org.ow2.asm:asm-tree:9.1")
  implementation("org.apache.httpcomponents:httpclient:4.5.13")
  implementation("org.gradle:test-retry-gradle-plugin:1.2.1")
  // When updating, also update dependencyManagement/dependencyManagement.gradle.kts
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.11.18")
  implementation("gradle.plugin.io.morethan.jmhreport:gradle-jmh-report:0.9.0")
  implementation("me.champeau.jmh:jmh-gradle-plugin:0.6.5")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:2.0.1")
  implementation("net.ltgt.gradle:gradle-nullaway-plugin:1.1.0")
  implementation("me.champeau.gradle:japicmp-gradle-plugin:0.3.0")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
  testImplementation("org.assertj:assertj-core:3.19.0")
}
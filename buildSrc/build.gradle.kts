plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
  // When updating, update below in dependencies too
  id("com.diffplug.spotless") version "5.13.0"
}

spotless {
  java {
    googleJavaFormat("1.10.0")
    licenseHeaderFile(rootProject.file("../gradle/enforcement/spotless.license.java"), "(package|import|public)")
    target("src/**/*.java")
  }
}

gradlePlugin {
  plugins {
    create("muzzle-plugin") {
      id = "muzzle"
      implementationClass = "io.opentelemetry.instrumentation.gradle.muzzle.MuzzlePlugin"
    }
  }
}

repositories {
  mavenCentral()
  gradlePluginPortal()
  mavenLocal()
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

dependencies {
  implementation(gradleApi())
  implementation(localGroovy())

  implementation("org.eclipse.aether:aether-connector-basic:1.1.0")
  implementation("org.eclipse.aether:aether-transport-http:1.1.0")
  implementation("org.apache.maven:maven-aether-provider:3.3.9")

  // When updating, update above in plugins too
  implementation("com.diffplug.spotless:spotless-plugin-gradle:5.13.0")
  implementation("com.google.guava:guava:30.1-jre")
  implementation("gradle.plugin.com.github.jengelman.gradle.plugins:shadow:7.0.0")
  implementation("org.ow2.asm:asm:7.0-beta")
  implementation("org.ow2.asm:asm-tree:7.0-beta")
  implementation("org.apache.httpcomponents:httpclient:4.5.10")
  implementation("org.gradle:test-retry-gradle-plugin:1.2.1")
  // When updating, also update dependencyManagement/dependencyManagement.gradle.kts
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.11.2")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:2.0.1")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.1")
  testImplementation("org.assertj:assertj-core:3.19.0")
}

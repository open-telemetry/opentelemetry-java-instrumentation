plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.2")
  implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.1")
  implementation("net.ltgt.gradle:gradle-nullaway-plugin:3.2.0")
}

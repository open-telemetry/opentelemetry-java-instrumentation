plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation("com.diffplug.spotless:spotless-plugin-gradle:8.4.0")
  implementation("com.gradleup.shadow:shadow-gradle-plugin:9.4.1")
}

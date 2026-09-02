plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.1")
  implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
}

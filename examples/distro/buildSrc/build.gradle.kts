plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation("com.gradleup.shadow:shadow-gradle-plugin:9.5.1")
}

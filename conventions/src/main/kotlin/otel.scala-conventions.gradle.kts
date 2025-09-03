// Enable testing scala code in groovy spock tests.

plugins {
  scala
}

dependencies {
  compileOnly("org.scala-lang:scala-library:2.11.12")
  testCompileOnly("org.scala-lang:scala-library:2.11.12")
}

tasks {
  // Gradle sets scala compiler version to toolchain version, not target version
  // https://github.com/gradle/gradle/issues/18211
  withType<ScalaCompile>().configureEach {
    scalaCompileOptions.apply {
      additionalParameters = additionalParameters.orEmpty() + "-target:jvm-1.8"
    }
  }
}

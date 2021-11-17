// Enable testing scala code in groovy spock tests.

plugins {
  scala
}

dependencies {
  testImplementation("org.scala-lang:scala-library")
}

tasks {
  named<GroovyCompile>("compileTestGroovy") {
    sourceSets.test {
      classpath = classpath.plus(files(scala.classesDirectory))
    }
  }

  // Gradle sets scala compiler version to toolchain vesion, not target version
  // https://github.com/gradle/gradle/issues/18211
  withType<ScalaCompile>().configureEach {
    scalaCompileOptions.apply {
      additionalParameters = additionalParameters.orEmpty() + "-target:jvm-1.8"
    }
  }
}

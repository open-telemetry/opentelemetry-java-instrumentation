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
}

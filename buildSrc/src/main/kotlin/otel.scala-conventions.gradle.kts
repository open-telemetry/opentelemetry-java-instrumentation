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
      withConvention(ScalaSourceSet::class) {
        classpath = classpath.plus(files(scala.classesDirectory))
      }
    }
  }
}

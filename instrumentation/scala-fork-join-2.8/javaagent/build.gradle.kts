plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
  id("org.unbroken-dome.test-sets")
}

muzzle {
  pass {
    group.set("org.scala-lang")
    module.set("scala-library")
    versions.set("[2.8.0,2.12.0)")
    assertInverse.set(true)
  }
}

testSets {
  create("slickTest")
}

dependencies {
  library("org.scala-lang:scala-library:2.8.0")

  latestDepTestLibrary("org.scala-lang:scala-library:2.11.+")

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))

  testImplementation(project(":instrumentation:executors:testing"))

  add("slickTestImplementation", project(":testing-common"))
  add("slickTestImplementation", "org.scala-lang:scala-library")
  add("slickTestImplementation", "com.typesafe.slick:slick_2.11:3.2.0")
  add("slickTestImplementation", "com.h2database:h2:1.4.197")
}

// Run Slick library tests along with the rest of tests
tasks {
  val slickTest by existing

  check {
    dependsOn(slickTest)
  }

  named<GroovyCompile>("compileSlickTestGroovy") {
    classpath = classpath.plus(files(sourceSets["slickTest"].scala.classesDirectory))
  }
}

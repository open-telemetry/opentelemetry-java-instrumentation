plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

dependencies {
  bootstrap(project(":instrumentation:jdbc:bootstrap"))
  compileOnly(
    project(
      path = ":instrumentation:jdbc:library",
      configuration = "shadow"
    )
  )
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  // jdbc unit testing
  testLibrary("com.h2database:h2:1.3.169")
  // first version jdk 1.6 compatible
  testLibrary("org.apache.derby:derby:10.6.1.0")
  testLibrary("org.hsqldb:hsqldb:2.0.0")

  testLibrary("org.apache.tomcat:tomcat-jdbc:7.0.19")
  // tomcat needs this to run
  testLibrary("org.apache.tomcat:tomcat-juli:7.0.19")
  testLibrary("com.zaxxer:HikariCP:2.4.0")
  testLibrary("com.mchange:c3p0:0.9.5")

  latestDepTestLibrary("org.apache.derby:derby:10.14.+")

  testImplementation(project(":instrumentation:jdbc:testing"))
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:jdbc:library")
    output.dir(
      shadedDep.file("build/extracted/shadow-javaagent"),
      "builtBy" to ":instrumentation:jdbc:library:extractShadowJarJavaagent"
    )
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.jdbc-datasource.enabled=true")
}

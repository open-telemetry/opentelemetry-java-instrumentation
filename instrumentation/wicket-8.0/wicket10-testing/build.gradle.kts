plugins {
  id("otel.javaagent-testing")
}

dependencies {
  library("org.apache.wicket:wicket:10.0.0")

  testImplementation(project(":instrumentation:wicket-8.0:common-testing"))
  testImplementation("org.eclipse.jetty:jetty-server:11.0.0")
  testImplementation("org.eclipse.jetty:jetty-servlet:11.0.0")

  testInstrumentation(project(":instrumentation:wicket-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  val testV3Preview by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testV3Preview)
  }
}

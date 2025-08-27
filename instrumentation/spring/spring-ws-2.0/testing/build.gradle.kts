plugins {
  id("com.github.bjornvester.xjc")
  id("otel.java-conventions")
}

sourceSets {
  main {
    resources {
      srcDirs("src/main/schema")
    }
  }
}

tasks {
  named<Checkstyle>("checkstyleMain") {
    // exclude generated web service classes
    exclude("**/hello_web_service/**")
  }
}

xjc {
  xsdDir.set(layout.projectDirectory.dir("src/main/schema"))
  useJakarta.set(false)
}

dependencies {
}

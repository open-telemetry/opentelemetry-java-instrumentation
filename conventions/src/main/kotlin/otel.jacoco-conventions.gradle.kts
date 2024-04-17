plugins {
  jacoco
}

jacoco {
  toolVersion = "0.8.12"
}

tasks {
  named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")

    reports {
      xml.required.set(true)
      csv.required.set(false)
      html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/"))
    }
  }
}

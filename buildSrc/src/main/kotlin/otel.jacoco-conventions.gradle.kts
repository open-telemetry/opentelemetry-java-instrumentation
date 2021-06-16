plugins {
  jacoco
}

jacoco {
  toolVersion = "0.8.6"
}

tasks {
  named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")

    reports {
      xml.isEnabled = true
      csv.isEnabled = false
      html.destination = file("${buildDir}/reports/jacoco/")
    }
  }
}

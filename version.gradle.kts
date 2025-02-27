val stableVersion = "2.13.2"
val alphaVersion = "2.13.2-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}

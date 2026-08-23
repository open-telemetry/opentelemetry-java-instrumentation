val stableVersion = "2.31.1"
val alphaVersion = "2.31.1-alpha"

val apidiffBaselineVersion = "2.30.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}

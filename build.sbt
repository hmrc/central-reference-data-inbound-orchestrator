import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.6.3"

lazy val microservice = Project("central-reference-data-inbound-orchestrator", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(CodeCoverageSettings.settings *)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions ++= Seq("-Wconf:src=routes/.*:s", "-Wconf:msg=Flag.*repeatedly:s"),
    resolvers += Resolver.jcenterRepo,
    PlayKeys.playDefaultPort := 7250,
  )

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.it)

addCommandAlias("runAllChecks", ";clean;compile;scalafmtAll;coverage;test;it/test;coverageReport")

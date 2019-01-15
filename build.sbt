name := "spool"
organization := "io.github.idata-shopee"
version := "0.0.1"
scalaVersion := "2.12.4"

useGpg := true 
parallelExecution in Test := true

publishTo := sonatypePublishTo.value

libraryDependencies ++= Seq(
  // Log lib
  "io.github.idata-shopee" %% "klog" % "0.1.0",

  // taskqueue
  "io.github.idata-shopee" %% "taskqueue" % "0.1.0",

  // test suite
  "org.scalatest" %% "scalatest" % "3.0.1" % Test
)

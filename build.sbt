name := "spool"
organization := "io.github.lock-free"
version := "0.0.1"
scalaVersion := "2.12.4"

useGpg := true 
parallelExecution in Test := true

publishTo := sonatypePublishTo.value

libraryDependencies ++= Seq(
  // Log lib
  "io.github.lock-free" %% "klog" % "0.1.0",

  "io.netty" % "netty" % "3.7.0.Final",

  // test suite
  "org.scalatest" %% "scalatest" % "3.0.1" % Test
)

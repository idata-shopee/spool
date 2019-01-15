sonatypeProfileName := "io.github.idata-shopee"

publishMavenStyle := true

licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

import xerial.sbt.Sonatype._
sonatypeProjectHosting := Some(GitHubHosting("idata-shopee", "spool", "testinsight61@gmail.com"))

homepage := Some(url("https://github.com/idata-shopee/spool"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/idata-shopee/spool"),
    "scm:git@github.com:idata-shopee/spool.git"
  )
)

developers := List(
  Developer(id="ddchen", name="ddchen", email="chenjunyuwork@gmail.com", url=url("http://lovekino.github.io/"))
)

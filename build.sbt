name := "sboom"

version := "1.0"

scalaVersion := "2.11.12"
val spinalVersion = "1.8.0"

libraryDependencies ++= Seq(
  //  "org.scalatest" % "scalatest_2.11" % "2.2.1",
  "org.scalatest" % "scalatest_2.11" % "3.2.5" ,
  "com.github.spinalhdl" % "spinalhdl-core_2.11" % spinalVersion,
  "com.github.spinalhdl" % "spinalhdl-lib_2.11"  % spinalVersion,
  compilerPlugin("com.github.spinalhdl" % "spinalhdl-idsl-plugin_2.11" % spinalVersion)
)

fork := true

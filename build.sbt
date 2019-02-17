name := "Image Cropper"

scalaVersion := "2.12.7"

scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-language:reflectiveCalls"
)

lazy val osName = System.getProperty("os.name") match {
    case n if n.startsWith("Linux")   => "linux"
    case n if n.startsWith("Mac")     => "mac"
    case n if n.startsWith("Windows") => "win"
    case _                            => throw new Exception("Unknown platform!")
}

lazy val javaFXModules = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
libraryDependencies ++= javaFXModules.map(m =>
    "org.openjfx" % s"javafx-$m" % "11" classifier osName
)

assemblyMergeStrategy in assembly := {
    case PathList("module-info.class") => MergeStrategy.rename
    case x                             =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
}

mainClass in assembly := Some("com.worthlesscog.image.Launcher")

//> using scala 3.3.7
//> using dep org.typelevel::laika-io:1.3.2
//> using dep org.typelevel::cats-effect:3.6.3
//> using dep org.http4s::http4s-ember-server:0.23.33
//> using dep org.http4s::http4s-dsl:0.23.33

import cats.effect.{IO, IOApp, ExitCode}
import laika.api._
import laika.format._
import laika.io.syntax._
import laika.io.model.FilePath
import laika.theme.Theme
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.staticcontent._
import com.comcast.ip4s._
import fs2.io.file.Path

object Build extends IOApp {

  val siteDir = "target/site"

  def build: IO[Unit] = {
    val transformer = Transformer
      .from(Markdown)
      .to(HTML)
      .using(Markdown.GitHubFlavor)
      .parallel[IO]
      .withTheme(Theme.empty)
      .build

    transformer.use { t =>
      t.fromDirectory("src")
        .toDirectory(siteDir)
        .transform
    }.void
  }

  def run(args: List[String]): IO[ExitCode] = {
    val shouldServe = args.contains("--serve") || args.contains("-s")
    build >> (if (shouldServe) serve(parsePort(args)) else IO.unit).as(ExitCode.Success)
  }

  private def parsePort(args: List[String]): Port = {
    val portIdx = args.indexWhere(a => a == "--port" || a == "-p")
    val parsed = for {
      idx <- Option.when(portIdx >= 0 && portIdx + 1 < args.length)(portIdx)
      port <- Port.fromString(args(idx + 1))
    } yield port
    parsed.getOrElse(port"8080")
  }

  def serve(port: Port): IO[Unit] = {
    val app = Router("/" -> fileService[IO](FileService.Config(siteDir))).orNotFound

    IO.println(s"Serving site at http://localhost:$port") >>
    IO.println("Press Ctrl+C to stop") >>
    EmberServerBuilder.default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port)
      .withHttpApp(app)
      .build
      .useForever
  }
}
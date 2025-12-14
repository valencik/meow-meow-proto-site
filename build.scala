//> using scala 3.3.7
//> using dep org.typelevel::laika-io:1.3.2
//> using dep org.typelevel::cats-effect:3.6.3
//> using dep org.http4s::http4s-ember-server:0.23.33
//> using dep ch.qos.logback:logback-classic:1.5.22
//> using dep pink.cozydev::protosearch-laika:0.0-cc3acd4-SNAPSHOT
//> using resourceDir resources
//> using repository https://central.sonatype.com/repository/maven-snapshots

package tlsite

import cats.effect.IO
import cats.syntax.all._

object Build {
  import laika.api._
  import laika.format._
  import laika.io.syntax._
  import pink.cozydev.protosearch.analysis.IndexFormat
  import pink.cozydev.protosearch.ui.SearchUI

  val outputDir = "target/site"

  private val parserBldr = MarkupParser.of(Markdown).using(Markdown.GitHubFlavor)
  private val parser = parserBldr.parallel[IO].build
  private val config = parserBldr.config

  private val mkHtml =
    Renderer.of(HTML).withConfig(config).parallel[IO].withTheme(SearchUI.standalone).build

  private val mkIndex =
    Renderer.of(IndexFormat).withConfig(config).parallel[IO].build

  def run: IO[Unit] = (parser, mkHtml, mkIndex).tupled.use((p, html, idx) =>
    p.fromDirectory("src").parse.flatMap { tree =>
      html.from(tree).toDirectory(outputDir).render >>
      idx.from(tree.root).toFile(SearchUI.indexPath(outputDir)).render
    }
  )
}

object Server {
  import org.http4s.ember.server.EmberServerBuilder
  import org.http4s.server.Router
  import org.http4s.server.middleware.Logger as RequestLogger
  import org.http4s.server.staticcontent._
  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  import com.comcast.ip4s._

  given LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val logger = LoggerFactory[IO].getLogger

  def run(port: Port): IO[Unit] = {
    val routes = Router("/" -> fileService[IO](FileService.Config(Build.outputDir))).orNotFound
    val app = RequestLogger.httpApp(logHeaders = false, logBody = false)(routes)

    logger.info(s"Serving site at http://localhost:$port") >>
    logger.info("Press Ctrl+C to stop") >>
    EmberServerBuilder.default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port)
      .withHttpApp(app)
      .build
      .useForever
  }
}

object Main extends cats.effect.IOApp {
  import cats.effect.ExitCode
  import com.comcast.ip4s._

  def run(args: List[String]): IO[ExitCode] = {
    val shouldServe = args.contains("--serve") || args.contains("-s")
    Build.run >> (if (shouldServe) Server.run(parsePort(args)) else IO.unit).as(ExitCode.Success)
  }

  private def parsePort(args: List[String]): Port = {
    val portIdx = args.indexWhere(a => a == "--port" || a == "-p")
    val parsed = for {
      idx <- Option.when(portIdx >= 0 && portIdx + 1 < args.length)(portIdx)
      port <- Port.fromString(args(idx + 1))
    } yield port
    parsed.getOrElse(port"8080")
  }
}
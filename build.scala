//> using dep org.http4s::http4s-ember-server::0.23.33
//> using dep org.typelevel::laika-preview::1.3.2
//> using dep com.monovore::decline-effect::2.5.0
//> using dep pink.cozydev::protosearch-laika:0.0-cc3acd4-SNAPSHOT
//> using repository https://central.sonatype.com/repository/maven-snapshots

import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.monovore.decline.*
import com.monovore.decline.effect.CommandIOApp
import fs2.io.file.Files
import fs2.io.file.Path
import laika.api.MarkupParser
import laika.api.Renderer
import laika.format.HTML
import laika.format.Markdown
import laika.io.model.FilePath
import laika.io.model.InputTree
import laika.io.syntax.*
import laika.preview.ServerBuilder
import laika.preview.ServerConfig
import org.http4s.server.Server
import pink.cozydev.protosearch.analysis.IndexFormat
import pink.cozydev.protosearch.analysis.IndexRendererConfig
import pink.cozydev.protosearch.ui.SearchUI
import java.net.URL
import laika.ast.Path.Root
import laika.api.format.TagFormatter
import laika.ast.Element
import laika.ast.Header
import laika.ast.SpanLink
import laika.ast.RelativePath.CurrentDocument
import laika.ast.Styles
import laika.theme.Theme
import laika.theme.ThemeBuilder
import laika.theme.ThemeProvider
import laika.ast.Literal

object Build extends CommandIOApp("build", "builds the site") {

  enum Subcommand {
    case Serve(port: Port)
    case Build(output: Path)
  }

  def opts = {
    val portOpt = Opts
      .option[Int]("port", "bind to this port")
      .mapValidated(Port.fromInt(_).toValidNel("Invalid port"))
      .withDefault(port"8000")

    val destinationOpt = Opts
      .option[String]("out", "site output directory")
      .map(Path(_))
      .withDefault(Path("target"))

    Opts
      .subcommand("serve", "serve the site")(
        portOpt.map(Subcommand.Serve(_))
      )
      .orElse(destinationOpt.map(Subcommand.Build(_)))
  }

  def main = opts.map {

    case Subcommand.Build(destination) =>
      Files[IO].deleteRecursively(destination).voidError *>
        build(FilePath.fromFS2Path(destination)).as(ExitCode.Success)

    case Subcommand.Serve(port) =>
      val serverConfig = ServerConfig.defaults
        .withPort(port)
        .withBinaryRenderers(List(IndexRendererConfig(true)))
      val server = ServerBuilder[IO](parser, input)
        .withConfig(serverConfig)
        .build
      server.evalTap(logServer(_)).useForever
  }

  def logServer(server: Server) =
    IO.println(s"Serving site at ${server.baseUri}")

  def input = {
    val securityPolicy = new URL(
      "https://raw.githubusercontent.com/typelevel/.github/refs/heads/main/SECURITY.md"
    )

    InputTree[IO]
      .addDirectory("src")
      .addInputStream(
        IO.blocking(securityPolicy.openStream()),
        Root / "security.md"
      )
  }

  def theme = {
    val provider = new ThemeProvider {
      def build[F[_]: Async] = ThemeBuilder[F]("typelevel.org").addRenderOverrides(overrides).build
    }

    provider.extendWith(SearchUI.standalone)
  }

  def parser = MarkupParser
    .of(Markdown)
    .using(Markdown.GitHubFlavor)
    .parallel[IO]
    .withTheme(theme)
    .build

  def build(destination: FilePath) = parser.use { parser =>
    val html = Renderer
      .of(HTML)
      .withConfig(parser.config)
      .parallel[IO]
      .withTheme(theme)
      .build
    val index =
      Renderer.of(IndexFormat).withConfig(parser.config).parallel[IO].build

    html.product(index).use { (html, index) =>
      parser.fromInput(input).parse.flatMap { tree =>
        html.from(tree).toDirectory(destination).render *>
          index
            .from(tree)
            .toFile(destination / "search" / "searchIndex.idx")
            .render
      }
    }
  }

  def overrides = HTML.Overrides {
    case (fmt, h: Header) =>
      val link = h.options.id.map { id =>
        SpanLink
          .internal(CurrentDocument(id))(Literal("", Styles("fas", "fa-link", "fa-sm")))
          .withOptions(
            Styles("anchor-link")
          )
      }
      val linkedContent = link.toList ++ h.content
      fmt.newLine + fmt.element(
        "h" + h.level.toString,
        h.withContent(linkedContent)
      )
  }

}

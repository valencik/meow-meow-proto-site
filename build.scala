//> using dep org.http4s::http4s-ember-server::0.23.33
//> using dep org.typelevel::laika-preview::1.3.2
//> using dep com.monovore::decline-effect::2.5.0
//> using dep pink.cozydev::protosearch-laika:0.0-fdae301-SNAPSHOT
//> using repository https://central.sonatype.com/repository/maven-snapshots
//> using option -deprecation

import cats.effect.*
import cats.syntax.all.*
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp

// Welcome to the typelevel.org build script!
// This script builds the site and can serve it locally for previewing.
//
// Main -- Entry point
// LaikaBuild -- Laika build, markdown in html out
// LaikaCustomizations -- Custom directives

object Main extends CommandIOApp("build", "builds the site") {
  import com.comcast.ip4s.*
  import fs2.io.file.{Files, Path}
  import laika.io.model.FilePath
  import laika.preview.{ServerBuilder, ServerConfig}
  import org.http4s.server.Server

  enum Subcommand {
    case Serve(port: Port)
    case Build(output: Path)
  }

  val portOpt = Opts
    .option[Int]("port", "bind to this port")
    .mapValidated(Port.fromInt(_).toValidNel("Invalid port"))
    .withDefault(port"8000")

  val destinationOpt = Opts
    .option[String]("out", "site output directory")
    .map(Path(_))
    .withDefault(Path("target"))

  val opts = Opts
    .subcommand("serve", "serve the site")(portOpt.map(Subcommand.Serve(_)))
    .orElse(destinationOpt.map(Subcommand.Build(_)))

  def main = opts.map {
    case Subcommand.Build(destination) =>
      Files[IO].deleteRecursively(destination).voidError *>
        LaikaBuild.build(FilePath.fromFS2Path(destination)).as(ExitCode.Success)

    case Subcommand.Serve(port) =>
      val serverConfig = ServerConfig.defaults
        .withPort(port)
        .withBinaryRenderers(LaikaBuild.binaryRenderers)
      val server = ServerBuilder[IO](LaikaBuild.parser, LaikaBuild.input)
        .withConfig(serverConfig)
        .build
      server.evalTap(logServer(_)).useForever
  }

  def logServer(server: Server) =
    IO.println(s"Serving site at ${server.baseUri}")
}

object LaikaBuild {
  import java.net.{URI, URL}
  import laika.api.MarkupParser
  import laika.api.Renderer
  import laika.api.format.TagFormatter
  import laika.ast.*
  import laika.config.SyntaxHighlighting
  import laika.format.{HTML, Markdown}
  import laika.io.model.{FilePath, InputTree}
  import laika.io.syntax.*
  import laika.parse.code.languages.ScalaSyntax
  import laika.theme.*
  import pink.cozydev.protosearch.analysis.{IndexFormat, IndexRendererConfig}
  import pink.cozydev.protosearch.ui.SearchUI

  def input = {
    val securityPolicy = new URI(
      "https://raw.githubusercontent.com/typelevel/.github/refs/heads/main/SECURITY.md"
    ).toURL()

    InputTree[IO]
      .addDirectory("src")
      .addInputStream(
        IO.blocking(securityPolicy.openStream()),
        Path.Root / "security.md"
      )
      .addClassResource[this.type](
        "laika/helium/css/code.css",
        Path.Root / "css" / "code.css"
      )
  }

  def theme = {
    val provider = new ThemeProvider {
      def build[F[_]: Async] =
        ThemeBuilder[F]("typelevel.org")
          .addRenderOverrides(LaikaCustomizations.overrides)
          .build
    }

    provider.extendWith(SearchUI.standalone)
  }

  def parser = MarkupParser
    .of(Markdown)
    .using(
      Markdown.GitHubFlavor,
      SyntaxHighlighting.withSyntaxBinding("scala", ScalaSyntax.Scala3),
      LaikaCustomizations.Directives
    )
    .parallel[IO]
    .withTheme(theme)
    .build

  val binaryRenderers = List(IndexRendererConfig(true))

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
}

object LaikaCustomizations {
  import java.time.OffsetDateTime
  import laika.api.bundle.{DirectiveRegistry, TemplateDirectives}
  import laika.api.config.*
  import laika.api.format.TagFormatter
  import laika.ast.*
  import laika.format.HTML

  def addAnchorLinks(fmt: TagFormatter, h: Header) = {
    val link = h.options.id.map { id =>
      SpanLink
        .internal(RelativePath.CurrentDocument(id))(
          Literal("", Styles("fas", "fa-link", "fa-sm"))
        )
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

  val overrides = HTML.Overrides { case (fmt, h: Header) =>
    addAnchorLinks(fmt, h)
  }

  object Directives extends DirectiveRegistry {
    import TemplateDirectives.dsl.*

    val templateDirectives = Seq(
      // custom Laika template directive for listing blog posts
      TemplateDirectives.eval("forBlogPosts") {
        (cursor, parsedBody, source).mapN { (c, b, s) =>
          def contentScope(value: ConfigValue) =
            TemplateScope(TemplateSpanSequence(b), value, s)

          val posts = c.parent.allDocuments.flatMap { d =>
            d.config.get[OffsetDateTime]("date").toList.tupleLeft(d)
          }

          posts
            .sortBy(_._2)(using summon[Ordering[OffsetDateTime]].reverse)
            .traverse { (d, _) =>
              d.config.get[ConfigValue]("").map(contentScope(_))
            }
            .leftMap(_.message)
            .map(TemplateSpanSequence(_))
        }
      }
    )

    val linkDirectives = Seq.empty
    val spanDirectives = Seq.empty
    val blockDirectives = Seq.empty
  }
}

//> using dep org.http4s::http4s-ember-server::0.23.33
//> using dep org.typelevel::laika-preview::1.3.2
//> using dep com.monovore::decline-effect::2.5.0
//> using dep pink.cozydev::protosearch-laika:0.0-cc3acd4-SNAPSHOT
//> using repository https://central.sonatype.com/repository/maven-snapshots
//> using option -deprecation

import cats.data.NonEmptyChain
import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.monovore.decline.*
import com.monovore.decline.effect.CommandIOApp
import fs2.io.file.Files
import fs2.io.file.Path
import laika.api.MarkupParser
import laika.api.Renderer
import laika.api.bundle.DirectiveRegistry
import laika.api.bundle.SpanDirectives
import laika.api.bundle.SpanDirectives.dsl.*
import laika.api.format.TagFormatter
import laika.ast.Element
import laika.ast.Header
import laika.ast.Literal
import laika.ast.Path.Root
import laika.ast.RelativePath.CurrentDocument
import laika.ast.SpanLink
import laika.ast.Styles
import laika.ast.Text
import laika.config.SyntaxHighlighting
import laika.format.HTML
import laika.format.Markdown
import laika.io.model.FilePath
import laika.io.model.InputTree
import laika.io.syntax.*
import laika.parse.code.languages.ScalaSyntax
import laika.preview.ServerBuilder
import laika.preview.ServerConfig
import laika.theme.Theme
import laika.theme.ThemeBuilder
import laika.theme.ThemeProvider
import org.http4s.server.Server
import pink.cozydev.protosearch.analysis.IndexFormat
import pink.cozydev.protosearch.analysis.IndexRendererConfig
import pink.cozydev.protosearch.ui.SearchUI

import java.net.URI
import java.net.URL
import laika.api.bundle.TemplateDirectives
import java.time.OffsetDateTime
import laika.api.config.ConfigValue
import laika.ast.DocumentCursor
import laika.ast.RawLink
import laika.ast.TemplateScope
import laika.ast.TemplateSpanSequence
import laika.api.config.ConfigValue.ASTValue
import laika.api.config.ConfigValue.ObjectValue
import laika.api.config.Field

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
    val securityPolicy = new URI(
      "https://raw.githubusercontent.com/typelevel/.github/refs/heads/main/SECURITY.md"
    ).toURL()

    InputTree[IO]
      .addDirectory("src")
      .addInputStream(
        IO.blocking(securityPolicy.openStream()),
        Root / "security.md"
      )
      .addClassResource[this.type](
        "laika/helium/css/code.css",
        Root / "css" / "code.css"
      )
  }

  def theme = {
    val provider = new ThemeProvider {
      def build[F[_]: Async] =
        ThemeBuilder[F]("typelevel.org").addRenderOverrides(overrides).build
    }

    provider.extendWith(SearchUI.standalone)
  }

  def parser = MarkupParser
    .of(Markdown)
    .using(
      Markdown.GitHubFlavor,
      SyntaxHighlighting.withSyntaxBinding("scala", ScalaSyntax.Scala3),
      Directives
    )
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

  object Directives extends DirectiveRegistry {
    val spanDirectives = Seq()
    val blockDirectives = Seq()
    val templateDirectives = Seq(
      // custom Laika template directive for listing blog posts
      TemplateDirectives.eval("forBlogPosts") {
        import TemplateDirectives.dsl.*

        (cursor, parsedBody, source).mapN { (c, b, s) =>
          // Create scope with config values and href field
          def contentScope(doc: DocumentCursor, config: ConfigValue) = {
            val hrefField = Field("href", ASTValue(RawLink.internal(doc.path)))
            val contextWithHref = config match {
              case obj: ObjectValue => ObjectValue(obj.values :+ hrefField)
              case v                => v
            }
            TemplateScope(TemplateSpanSequence(b), contextWithHref, s)
          }

          val posts = c.parent.allDocuments.flatMap { d =>
            d.config.get[OffsetDateTime]("date").toList.tupleLeft(d)
          }

          posts
            .sortBy(_._2)(using summon[Ordering[OffsetDateTime]].reverse)
            .traverse { (d, _) =>
              d.config.get[ConfigValue]("").map(contentScope(d, _))
            }
            .leftMap(_.message)
            .map(TemplateSpanSequence(_))
        }
      }
    )

    val linkDirectives = Seq()
  }

  def overrides = HTML.Overrides { case (fmt, h: Header) =>
    val link = h.options.id.map { id =>
      SpanLink
        .internal(CurrentDocument(id))(
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

}

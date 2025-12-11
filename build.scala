//> using scala 3.3.7
//> using dep org.typelevel::laika-io:1.3.2
//> using dep org.typelevel::cats-effect:3.6.3

import cats.effect.{IO, IOApp, ExitCode}
import laika.api._
import laika.format._
import laika.io.syntax._
import laika.io.model.FilePath
import laika.theme.Theme

object Build extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val transformer = Transformer
      .from(Markdown)
      .to(HTML)
      .using(Markdown.GitHubFlavor)
      .parallel[IO]
      .withTheme(Theme.empty)  // Use empty theme, we provide our own templates
      .build

    transformer.use { t =>
      t.fromDirectory("src")
        .toDirectory("target/site")
        .transform
    }.as(ExitCode.Success)
  }
}


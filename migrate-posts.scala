//> using dep org.virtuslab::scala-yaml::0.3.1
//> using dep co.fs2::fs2-io::3.12.2

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import fs2.io.file.{Files, Path}
import org.virtuslab.yaml.*

case class PostMeta(author: Option[String]) derives YamlCodec

case class Conf(title: String, category: Option[String], meta: Option[PostMeta])
    derives YamlCodec

case class Post(conf: Conf, content: String) {
  def toLaika(date: String): String = {
    val authorLine = conf.meta.flatMap(_.author).map(a => s"  author: $${$a}")
    val dateLine = Some(s"""  date: "$date"""")
    val tagsLine = conf.category.map(c => s"  tags: [$c]")

    val metadata = List(
      Some("{%"),
      authorLine,
      dateLine,
      tagsLine,
      Some("%}")
    ).flatten.mkString("\n")

    val titleLine = s"# ${conf.title}"

    s"$metadata\n\n$titleLine\n\n$content\n"
  }
}

object PostParser {
  def parse(content: String): Either[Throwable, Post] = {
    val parts = content.split("---\n", 3)
    if (parts.length < 3) {
      Left(new Exception("Invalid post format: no YAML front matter found"))
    } else {
      val yamlContent = parts(1)
      val markdownContent = parts(2).trim
      yamlContent.as[Conf].map(conf => Post(conf, markdownContent))
    }
  }
}

object MigratePosts extends IOApp.Simple {
  val oldPostsDir = Path("../typelevel.github.com/collections/_posts")
  val newBlogDir = Path("src/blog")

  def getDateAndName(path: Path): Either[Throwable, (String, String)] = {
    val filename = path.fileName.toString
    val datePattern = """(\d{4}-\d{2}-\d{2})-(.+)""".r
    filename match {
      case datePattern(date, rest) => Right((date, rest))
      case _                       =>
        Left(new Exception(s"Filename doesn't match pattern: $filename"))
    }
  }

  def readPost(path: Path): IO[String] = Files[IO]
    .readAll(path)
    .through(fs2.text.utf8.decode)
    .compile
    .string

  def writePost(path: Path, content: String): IO[Unit] = fs2.Stream
    .emit(content)
    .through(fs2.text.utf8.encode)
    .through(Files[IO].writeAll(path))
    .compile
    .drain

  def migratePost(sourcePath: Path): IO[String] = for {
    (date, newFilename) <- IO.fromEither(getDateAndName(sourcePath))
    content <- readPost(sourcePath)
    post <- IO.fromEither(PostParser.parse(content))
    laikaContent = post.toLaika(date)
    destPath = newBlogDir / newFilename
    _ <- writePost(destPath, laikaContent)
  } yield newFilename

  def migrateAllPosts: IO[Long] = Files[IO]
    .list(oldPostsDir)
    .filter(_.fileName.toString.endsWith(".md"))
    .evalMap(path => migratePost(path))
    .evalMap(newFilename => IO.println(s"Migrated: $newFilename"))
    .compile
    .count

  val run: IO[Unit] =
    migrateAllPosts.flatMap(c => IO.println(s"Migrated $c posts"))
}

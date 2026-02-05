//> using dep org.virtuslab::scala-yaml::0.3.1
//> using dep co.fs2::fs2-io::3.12.2
//> using dep com.typesafe:config:1.4.5

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import fs2.io.file.{Files, Path}
import org.virtuslab.yaml.*
import com.typesafe.config.{Config, ConfigFactory}
import scala.jdk.CollectionConverters.*

case class ScheduleItem(
  time: String,
  title: String,
  speakers: Option[List[String]] = None,
  summary: Option[String] = None
) derives YamlCodec

case class Sponsor(
  name: String,
  logo: String,
  link: String,
  `type`: String,
  height: Option[Int] = None
) derives YamlCodec

case class EventConfig(
  title: String,
  short_title: Option[String] = None,
  date_string: String,
  location: String,
  description: String,
  poster_hero: Option[String] = None,
  poster_thumb: Option[String] = None,
  schedule: Option[List[ScheduleItem]] = None,
  sponsors: Option[List[Sponsor]] = None
) derives YamlCodec

case class Event(conf: EventConfig, content: String, originalYaml: String) {

  def loadSpeakerDirectory(): Map[String, String] = {
    try {
      val config = ConfigFactory.parseFile(Path("src/blog/directory.conf").toNioPath.toFile)
      val speakerNames = config.root().keySet().asScala.toList.map { key =>
        key -> config.getConfig(key).getString("name")
      }.toMap
      speakerNames
    } catch {
      case _: Exception => Map.empty[String, String]
    }
  }

  def cleanPostUrl(markdown: String): String = {
    // Replace {% post_url YYYY-MM-DD-filename %} with filename.md
    val postUrlPattern = """\{\%\s*post_url\s+\d{4}-\d{2}-\d{2}-(.+?)\s*%\}""".r
    postUrlPattern.replaceAllIn(markdown, "$1.md")
  }

  def cleanOtherLinks(markdown: String): String = {
    var cleaned = markdown

    // Replace absolute typelevel.org blog URLs: https://typelevel.org/blog/YYYY/MM/DD/post-name.html with post-name.md
    val typelevelBlogPattern =
      """https://typelevel\.org/blog/\d{4}/\d{2}/\d{2}/([^)\s]+)\.html""".r
    cleaned = typelevelBlogPattern.replaceAllIn(cleaned, "$1.md")

    // Replace relative blog URLs: /blog/YYYY/MM/DD/post-name.html with post-name.md
    val relativeBlogPattern =
      """(?<![a-z])/blog/\d{4}/\d{2}/\d{2}/([^)\s]+)\.html""".r
    cleaned = relativeBlogPattern.replaceAllIn(cleaned, "$1.md")

    // Replace Jekyll site.url variables: {{ site.url }}/... with /...
    val siteUrlPattern = """\{\{\s*site\.url\s*\}\}""".r
    cleaned = siteUrlPattern.replaceAllIn(cleaned, "")

    // Replace .html extensions with .md in relative links (but not absolute URLs starting with http)
    val htmlToMdPattern = """(?<!https?://[^\s)]*)(\\.html)""".r
    cleaned = htmlToMdPattern.replaceAllIn(cleaned, ".md")

    // Fix code of conduct link
    cleaned = cleaned.replace("/conduct.html", "/code-of-conduct/README.md")

    cleaned
  }

  def buildHoconMetadata(date: String): String = {
    s"""{%
  date: "$date"
  tags: [summits, events]
%}"""
  }

  def generateScheduleHtml(): String = {
    conf.schedule.map { scheduleItems =>
      val tableRows = scheduleItems.map { item =>
        val timeCell = s"""        <td><strong>${item.time}</strong></td>"""

        val titleCell = if (item.speakers.isEmpty) {
          s"""        <td>${item.title}</td>"""
        } else {
          item.speakers.map { speakers =>
            val speakerDirectory = loadSpeakerDirectory()
            val speakerNames = speakers.map { shortname =>
              speakerDirectory.getOrElse(shortname, shortname)
            }.mkString(", ")

            val summary = item.summary.getOrElse("")

            s"""        <td>
          <div class="bulma-content">
            <h5 class="bulma-title bulma-is-6">${item.title}</h5>
            <p class="bulma-subtitle bulma-is-6">$speakerNames</p>
            <p>$summary</p>
          </div>
        </td>"""
          }.getOrElse(s"""        <td>${item.title}</td>""")
        }

        s"""      <tr>
$timeCell
$titleCell
      </tr>"""
      }.mkString("\n")

      s"""<div class="bulma-table-container">
  <table class="bulma-table bulma-is-striped bulma-is-hoverable bulma-is-fullwidth">
    <thead>
      <tr>
        <th>Time</th>
        <th>Talk</th>
      </tr>
    </thead>
    <tbody>
$tableRows
    </tbody>
  </table>
</div>"""
    }.getOrElse("")
  }

  def generateSponsorsHtml(): String = {
    conf.sponsors.map { sponsors =>
      val sponsorsByType = sponsors.groupBy(_.`type`)

      val sections = List("platinum", "gold", "silver").flatMap { sponsorType =>
        sponsorsByType.get(sponsorType).map { typeSponsors =>
          val sponsorCells = typeSponsors.map { sponsor =>
            s"""  <div class="bulma-cell">
    <div class="bulma-has-text-centered">
      <a href="${sponsor.link}">
        <img src="${sponsor.logo}" alt="${sponsor.name}" title="${sponsor.name}" style="height:60px" />
      </a>
    </div>
  </div>"""
          }.mkString("\n")

          s"""### ${sponsorType.capitalize}

<div class="bulma-grid bulma-is-col-min-12">
$sponsorCells
</div>"""
        }
      }

      sections.mkString("\n\n")
    }.getOrElse("")
  }

  def toLaika(date: String, stage: Int): String = {
    val metadata = buildHoconMetadata(date)
    val title = s"# ${conf.title}"
    val header = s"**${conf.date_string}** • **${conf.location}**"
    val description = conf.description
    val image = conf.poster_hero.map(img => s"![${conf.title}]($img)").getOrElse("")

    stage match {
      case 1 =>
        // Stage 1: Just move to new location, keep original format
        s"---\n$originalYaml---\n\n$content\n"

      case 2 =>
        // Stage 2: HOCON metadata + title, no content changes
        s"$metadata\n\n$title\n\n$header\n\n$description\n\n$image\n\n$content\n"

      case 3 =>
        // Stage 3: Stage 2 + link cleaning
        val transformedContent = cleanOtherLinks(cleanPostUrl(content))
        s"$metadata\n\n$title\n\n$header\n\n$description\n\n$image\n\n$transformedContent\n"

      case _ =>
        // Stage 4+: Use original content and replace Jekyll includes with generated HTML
        val transformedContent = cleanOtherLinks(cleanPostUrl(content))

        // Replace Jekyll includes with generated HTML
        var processedContent = transformedContent

        // Remove schedule assign and replace schedule include
        val scheduleAssignPattern = """\{\%\s*assign\s+schedule\s*=\s*page\.schedule\s*%\}\s*""".r
        processedContent = scheduleAssignPattern.replaceAllIn(processedContent, "")

        val schedulePattern = """\{\%\s*include\s+schedule\.html\s*%\}""".r
        val scheduleReplacement = if (conf.schedule.isDefined) generateScheduleHtml() else ""
        processedContent = schedulePattern.replaceAllIn(processedContent, scheduleReplacement)

        // Replace sponsors include
        val sponsorsPattern = """\{\%\s*include\s+sponsors\.html\s*%\}""".r
        val sponsorsReplacement = if (conf.sponsors.isDefined) generateSponsorsHtml() else ""
        processedContent = sponsorsPattern.replaceAllIn(processedContent, sponsorsReplacement)

        // Remove venue_map includes (not supported)
        val venueMapPattern = """\{\%\s*include\s+venue_map\.html\s*%\}""".r
        processedContent = venueMapPattern.replaceAllIn(processedContent, "")

        s"$metadata\n\n$title\n\n$header\n\n$description\n\n$image\n\n$processedContent\n"
    }
  }
}

object EventParser {
  def parse(path: Path, content: String): Either[Throwable, Event] = {
    // Normalize Windows line endings to Unix
    val normalized = content.replace("\r\n", "\n")
    val parts = normalized.split("---\n", 3)
    if (parts.length < 3) {
      val fn = path.fileName
      Left(new Exception(s"Invalid event '$fn': no YAML front matter found"))
    } else {
      val yamlContent = parts(1)
      val markdownContent = parts(2).trim
      yamlContent.as[EventConfig].map(conf => Event(conf, markdownContent, yamlContent))
    }
  }
}

object MigrateEvents extends IOApp {
  val oldEventsDir = Path("../typelevel.github.com/collections/_events")
  val newBlogDir = Path("src/blog")

  def getDateAndName(path: Path): Either[Throwable, (String, String)] = {
    val filename = path.fileName.toString
    val datePattern = """(\d{4}-\d{2}-\d{2})-(.+)""".r
    filename match {
      case datePattern(date, rest) => Right((date, filename)) // Keep full filename
      case _ =>
        Left(new Exception(s"Filename doesn't match pattern: $filename"))
    }
  }

  def readEvent(path: Path): IO[String] = Files[IO]
    .readAll(path)
    .through(fs2.text.utf8.decode)
    .compile
    .string

  def writeEvent(path: Path, content: String): IO[Unit] = fs2.Stream
    .emit(content)
    .through(fs2.text.utf8.encode)
    .through(Files[IO].writeAll(path))
    .compile
    .drain

  def migrateEvent(sourcePath: Path, stage: Int): IO[String] = for {
    (date, fullFilename) <- IO.fromEither(getDateAndName(sourcePath))
    content <- readEvent(sourcePath)
    event <- IO.fromEither(EventParser.parse(sourcePath, content))
    laikaContent = event.toLaika(date, stage)
    destPath = newBlogDir / fullFilename
    _ <- writeEvent(destPath, laikaContent)
  } yield fullFilename

  def migrateAllEvents(stage: Int): IO[Long] = Files[IO]
    .list(oldEventsDir)
    .filter(_.fileName.toString.matches("""^\d{4}-\d{2}-\d{2}-.+\.md$"""))
    .evalMap(path => migrateEvent(path, stage))
    .evalMap(fullFilename => IO.println(s"Migrated: $fullFilename"))
    .compile
    .count

  def run(args: List[String]): IO[cats.effect.ExitCode] = {
    val stage = args.headOption.flatMap(_.toIntOption).getOrElse(4)
    IO.println(s"Running migration with stage $stage") *>
      migrateAllEvents(stage)
        .flatMap(c => IO.println(s"Migrated $c events"))
        .as(cats.effect.ExitCode.Success)
  }
}
import org.http4s.Uri

val test = Seq(
  "http://localhost",
  "localhost",
  "127.0.0.1",
  "http://host.docker.internal",
  "host.docker.internal"
)

test.map(Uri.unsafeFromString)
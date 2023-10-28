# Watchlistarr

Sync plex watchlists in realtime with Sonarr and Radarr. **Requires Plex Pass**

## Getting Started

The easiest way to try this code is using docker:

```bash
docker run \
  -e SONARR_API_KEY=YOUR_API_KEY \
  -e RADARR_API_KEY=YOUR_API_KEY \
  -e PLEX_WATCHLIST_URL_1=YOUR_PLEX_WATCHLIST_URL \
  nylonee/watchlistarr
```

| Key                      | Example Value                    | Optional | Description                                                              |
|--------------------------|----------------------------------|----------|--------------------------------------------------------------------------|
| REFRESH_INTERVAL_SECONDS | 60                               | Yes      | Number of seconds to wait in between checking the watchlist              |
| SONARR_API_KEY           | 7a392fb4817a46e59f2e84e7d5f021bc | No       | API key for Sonarr, found in your Sonarr UI -> General settings          |
| SONARR_BASE_URL          | http://localhost:8989            | Yes      | Base URL for Sonarr, including the 'http' and port                       |
| SONARR_QUALITY_PROFILE   | 1080p                            | Yes      | Quality profile for Sonarr, found in your Sonarr UI -> Profiles settings |
| SONARR_ROOT_FOLDER       | /data/                           | Yes      | Root folder for Sonarr                                                   |
| RADARR_API_KEY           | 7a392fb4817a46e59f2e84e7d5f021bc | No       | API key for Radarr, found in your Radarr UI -> General settings          |
| RADARR_BASE_URL          | http://127.0.0.1:7878            | Yes      | Base URL for Radarr, including the 'http' and port                       |
| RADARR_QUALITY_PROFILE   | 1080p                            | Yes      | Quality profile for Radarr, found in your Radarr UI -> Profiles settings |
| RADARR_ROOT_FOLDER       | /data/                           | Yes      | Root folder for Radarr                                                   |
| PLEX_WATCHLIST_URL_1     | https://rss.plex.tv/UUID         | No       | First Plex Watchlist URL                                                 |
| PLEX_WATCHLIST_URL_2     | https://rss.plex.tv/UUID         | Yes      | Second Plex Watchlist URL (if applicable)                                |

### Getting your Plex Watchlist URLs

1. Go to [Watchlist settings in Plex](https://app.plex.tv/desktop/#!/settings/watchlist)
2. Generate RSS Feeds for the watchlists you want to monitor, and copy those URLs

## Developers Corner

Build the docker image:

```
docker build -t nylonee/watchlistarr:latest -f docker/Dockerfile .
```

Run the docker image:

```
docker run nylonee/watchlistarr
```

Run the sbt version:

```
sbt run
```

Make a fat jar:

```
sbt assembly
```

(look in target/scala-2.13/watchlistarr-assembly-VERSION.jar)
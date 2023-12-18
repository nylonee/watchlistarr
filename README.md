# Watchlistarr

<img src="watchlistarr.png" alt="Alternate Text" width="240"/>

Sync plex watchlists in realtime with Sonarr and Radarr.

## How it works

There are several ways of fetching watchlist information from Plex

| Method                                  | Pros                                                                    | Cons                                                                                                            | Supported by Watchlistarr | Supported by Overseer/Ombi | Supported by Sonarr/Radarr             |
|-----------------------------------------|-------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|---------------------------|----------------------------|----------------------------------------|
| Plex Watchlist RSS Feeds                | Fast and instantly updated, can create one for self and one for friends | Will only show the most recent 50 movies/shows                                                                  | Yes                       | X                          | Only for self, refreshes every 6 hours |
| Plex Watchlist Metadata                 | Can fetch the full watchlist for the user                               | Need a plex token for each user who wants to have their watchlist synced, Plex token expires after a few months | Yes                       | Yes, up to 5 min intervals | X                                      |
| Plex Watchlist GraphQL Call             | Can fetch the full watchlist for every friend of the main user          | Slow, relies on main user providing a plex token                                                                | Yes                       | X                          | X                                      |
| Plex Watchlist Metadata for other users | Can fetch the full watchlist for every user who provides a token        | Difficult to ask every user to generate a Plex token, or log into a service                                     | Yes                       | Yes, up to 5 min intervals | X                                      |

In order to fully integrate with Plex Watchlists, Watchlistarr uses a combination of multiple methods to ensure that it
does a comprehensive, yet fast real-time sync.

### Full Delete Sync

Watchlistarr is working towards being able to support a full delete sync with your watchlist. This means that **if
a user
removes an item off their watchlist, Watchlistarr can detect that and delete content from Sonarr/Radarr.**

This feature will be released soon, in the meantime, you can enjoy a little "sneak peek"
upon startup of the app, where the logs will list the movies/tv shows that are out of sync.

### Requirements

* Plex Pass Subscription
* Sonarr v4 or higher
* Radarr v3 or higher
* Friends must change their privacy settings so that the main user can see their watchlists
* Docker or Java
* Plex Token (see [here](https://support.plex.tv/articles/204059436-finding-an-authentication-token-x-plex-token/))

## Getting Started

### Docker

The easiest way to try this code is using docker:

```bash
docker run \
  -e SONARR_API_KEY=YOUR_API_KEY \
  -e RADARR_API_KEY=YOUR_API_KEY \
  -e PLEX_TOKEN=YOUR_PLEX_TOKEN \
  -e REFRESH_INTERVAL_SECONDS=5 \
  nylonee/watchlistarr
```

For a full list of possible environment variables to configure the app with, see the Environment Variables section of
this Readme

Docker tag options:

* `latest` - Stable version, follows the Releases
* `beta` - Beta version, follows the main branch
* `alpha` - Experimental version, follows the latest successful PR build

### Java

Running this using native java requires the fat jar, download the latest from the Releases tab, and run:

```bash
java -Dsonarr.apikey=YOUR_API_KEY\
  -Dradarr.apikey=YOUR_API_KEY\
  -Dplex.token=YOUR_PLEX_TOKEN\
  -jar watchlistarr.java
```

For a full list of options to pass in when running the application on native java,
refer to the environment variables chart below, and cross-reference the key to the internal key
in [entrypoint.sh](https://github.com/nylonee/watchlistarr/blob/main/docker/entrypoint.sh)

### Environment Variables

| Key                      | Default               | Description                                                                                                                                                                                         |
|--------------------------|-----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SONARR_API_KEY*          |                       | API key for Sonarr, found in your Sonarr UI -> General settings                                                                                                                                     |
| RADARR_API_KEY*          |                       | API key for Radarr, found in your Radarr UI -> General settings                                                                                                                                     |
| PLEX_TOKEN*              |                       | Token for Plex, retrieved via [this tutorial](https://support.plex.tv/articles/204059436-finding-an-authentication-token-x-plex-token/). Note that multiple tokens can be provided, comma separated |
| REFRESH_INTERVAL_SECONDS | 60                    | Number of seconds to wait in between checking the watchlist                                                                                                                                         |
| SONARR_BASE_URL          | http://localhost:8989 | Base URL for Sonarr, including the 'http' and port and any configured urlbase                                                                                                                       |
| SONARR_QUALITY_PROFILE   |                       | Quality profile for Sonarr, found in your Sonarr UI -> Profiles settings. If not set, will grab the first one it finds on Sonarr                                                                    |
| SONARR_ROOT_FOLDER       |                       | Root folder for Sonarr. If not set, will grab the first one it finds on Sonarr                                                                                                                      |
| SONARR_BYPASS_IGNORED    | false                 | Boolean flag to bypass tv shows that are on the Sonarr Exclusion List                                                                                                                               |
| SONARR_SEASON_MONITORING | all                   | Default monitoring for new seasons added to Sonarr. Full list of options are found in the [Sonarr API Docs](https://sonarr.tv/docs/api/#/Series/post_api_v3_series) under **MonitorTypes**          |
| RADARR_BASE_URL          | http://127.0.0.1:7878 | Base URL for Radarr, including the 'http' and port and any configured urlbase                                                                                                                       |
| RADARR_QUALITY_PROFILE   |                       | Quality profile for Radarr, found in your Radarr UI -> Profiles settings. If not set, will grab the first one it finds on Radarr                                                                    |
| RADARR_ROOT_FOLDER       |                       | Root folder for Radarr. If not set, will grab the first one it finds on Radarr                                                                                                                      |
| RADARR_BYPASS_IGNORED    | false                 | Boolean flag to bypass movies that are on the Radarr Exclusion List                                                                                                                                 |
| SKIP_FRIEND_SYNC         | false                 | Boolean flag to toggle between only syncing your own content, vs syncing your own and all your friends content                                                                                      |

## Developers Corner

Build the docker image:

```
docker build -t nylonee/watchlistarr:latest -f docker/Dockerfile .
```

Run the docker image:

```
docker run nylonee/watchlistarr
```

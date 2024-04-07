# Watchlistarr

<img src="watchlistarr.png" alt="Alternate Text" width="240"/>

Sync plex watchlists in realtime with Sonarr and Radarr.

![watchlistarr-gif](https://github.com/nylonee/watchlistarr/assets/4732553/3ff083f2-5c5d-411b-8280-ea76958542bf)

## How it works

There are several ways of fetching watchlist information from Plex

| Method                                  | Pros                                                                    | Cons                                                                                                            | Supported by Watchlistarr | Supported by Overseer/Ombi   | Supported by Sonarr/Radarr             |
|-----------------------------------------|-------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|---------------------------|------------------------------|----------------------------------------|
| Plex Watchlist RSS Feeds                | Fast and instantly updated, can create one for self and one for friends | Will only show the most recent 50 movies/shows                                                                  | Yes                       | X                            | Only for self, refreshes every 6 hours |
| Plex Watchlist Metadata                 | Can fetch the full watchlist for the user                               | Need a plex token for each user who wants to have their watchlist synced, Plex token expires after a few months | Yes                       | Yes, up to ~20 min intervals | X                                      |
| Plex Watchlist GraphQL Call             | Can fetch the full watchlist for every friend of the main user          | Slow, relies on main user providing a plex token                                                                | Yes                       | X                            | X                                      |
| Plex Watchlist Metadata for other users | Can fetch the full watchlist for every user who provides a token        | Difficult to ask every user to generate a Plex token, or log into a service                                     | Yes                       | Yes, up to ~20 min intervals | X                                      |

In order to fully integrate with Plex Watchlists, Watchlistarr uses a combination of multiple methods to ensure that it
does a comprehensive, yet fast real-time sync.

### Full Delete Sync

Watchlistarr also supports a full delete sync with your watchlist. This means that **if
a user
removes an item off their watchlist, Watchlistarr can detect that and delete content from Sonarr/Radarr.**

Note: This feature is disabled by default
Whether you've enabled this or not, you can enjoy a little "sneak peek"
upon startup of the app, where the logs will list the movies/tv shows that are out of sync.

### Requirements

* Plex Pass Subscription (Recommended, otherwise see "Plex Pass Alternative" section below)
* Sonarr v3 or higher
* Radarr v3 or higher
* Friends' Watchlists [Account Visibility](https://app.plex.tv/desktop/#!/settings/account) must be changed to 'Friends
  Only' or 'Friends of Friends'
* Docker or Java v11 or higher (Recommended
  version [JDK 21](https://www.oracle.com/java/technologies/downloads/#jdk21-windows))
* Plex Token (see [here](https://support.plex.tv/articles/204059436-finding-an-authentication-token-x-plex-token/))

## Getting Started
See [Configuration](CONFIGURATION.md) for steps on getting started and configuring Watchlistarr

## Plex Pass Alternative

The Plex Pass subscription is required to generate the RSS Feed URLs. Without a Plex Pass, the normal API calls are too
heavy-hitting on Plex's servers.

If the app detects that you are not a Plex Pass user (i.e. the app tries to generate an RSS URL, and it fails), it will
fall back into a periodic sync.

The periodic sync will run every 19 minutes, ignoring the configuration for REFRESH_INTERVAL_SECONDS

All other settings will still be valid

## Developers Corner

Build the docker image:

```
docker build -t nylonee/watchlistarr:latest -f docker/Dockerfile .
```

Run the docker image:

```
docker run nylonee/watchlistarr
```

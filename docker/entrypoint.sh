#!/bin/bash
CMD=("/app/bin/watchlistarr")

JAVA_OPTS=(-Xmx100m)

if [ -n "$SONARR_API_KEY" ]; then
  CMD+=("-Dsonarr.apikey=$SONARR_API_KEY")
fi

if [ -n "$SONARR_BASE_URL" ]; then
  CMD+=("-Dsonarr.baseUrl=$SONARR_BASE_URL")
fi

if [ -n "$SONARR_QUALITY_PROFILE" ]; then
  CMD+=("-Dsonarr.qualityProfile=$SONARR_QUALITY_PROFILE")
fi

if [ -n "$SONARR_ROOT_FOLDER" ]; then
  CMD+=("-Dsonarr.rootFolder=$SONARR_ROOT_FOLDER")
fi

if [ -n "$RADARR_API_KEY" ]; then
  CMD+=("-Dradarr.apikey=$RADARR_API_KEY")
fi

if [ -n "$RADARR_BASE_URL" ]; then
  CMD+=("-Dradarr.baseUrl=$RADARR_BASE_URL")
fi

if [ -n "$RADARR_QUALITY_PROFILE" ]; then
  CMD+=("-Dradarr.qualityProfile=$RADARR_QUALITY_PROFILE")
fi

if [ -n "$RADARR_ROOT_FOLDER" ]; then
  CMD+=("-Dradarr.rootFolder=$RADARR_ROOT_FOLDER")
fi

if [ -n "$PLEX_WATCHLIST_URL_1" ]; then
  CMD+=("-Dplex.watchlist1=$PLEX_WATCHLIST_URL_1")
fi

if [ -n "$PLEX_WATCHLIST_URL_2" ]; then
  CMD+=("-Dplex.watchlist2=$PLEX_WATCHLIST_URL_2")
fi

if [ -n "$REFRESH_INTERVAL_SECONDS" ]; then
  CMD+=("-Dinterval.seconds=$REFRESH_INTERVAL_SECONDS")
fi

if [ -n "$SONARR_BYPASS_IGNORED" ]; then
  CMD+=("-Dsonarr.bypassIgnored=$SONARR_BYPASS_IGNORED")
fi

if [ -n "$RADARR_BYPASS_IGNORED" ]; then
  CMD+=("-Dradarr.bypassIgnored=$RADARR_BYPASS_IGNORED")
fi

if [ -n "$SONARR_SEASON_MONITORING" ]; then
  CMD+=("-Dsonarr.seasonMonitoring=$SONARR_SEASON_MONITORING")
fi

if [ -n "$PLEX_TOKEN" ]; then
  CMD+=("-Dplex.token=$PLEX_TOKEN")
fi

if [ -n "$SKIP_FRIEND_SYNC" ]; then
  CMD+=("-Dplex.skipfriendsync=$SKIP_FRIEND_SYNC")
fi

if [ -n "$ALLOW_MOVIE_DELETING" ]; then
  CMD+=("-Ddelete.movie=$ALLOW_MOVIE_DELETING")
fi

if [ -n "$ALLOW_ENDED_SHOW_DELETING" ]; then
  CMD+=("-Ddelete.endedShow=$ALLOW_ENDED_SHOW_DELETING")
fi

if [ -n "$ALLOW_CONTINUING_SHOW_DELETING" ]; then
  CMD+=("-Ddelete.continuingShow=$ALLOW_CONTINUING_SHOW_DELETING")
fi

if [ -n "$DELETE_INTERVAL_DAYS" ]; then
  CMD+=("-Ddelete.interval.days=$DELETE_INTERVAL_DAYS")
fi

if [ -n "$SONARR_TAGS" ]; then
  CMD+=("-Dsonarr.tags=$SONARR_TAGS")
fi

if [ -n "$RADARR_TAGS" ]; then
  CMD+=("-Dradarr.tags=$RADARR_TAGS")
fi

exec "${CMD[@]}" "${JAVA_OPTS[@]}"

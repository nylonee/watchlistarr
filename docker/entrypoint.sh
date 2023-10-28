#!/bin/bash

CMD="/app/bin/watchlistarr"

if [ -n "$SONARR_API_KEY" ]; then
  CMD="$CMD -Dsonarr.apikey=$SONARR_API_KEY"
fi

if [ -n "$SONARR_BASE_URL" ]; then
  CMD="$CMD -Dsonarr.baseUrl=$SONARR_BASE_URL"
fi

if [ -n "$SONARR_QUALITY_PROFILE" ]; then
  CMD="$CMD -Dsonarr.qualityProfile=$SONARR_QUALITY_PROFILE"
fi

if [ -n "$SONARR_ROOT_FOLDER" ]; then
  CMD="$CMD -Dsonarr.rootFolder=$SONARR_ROOT_FOLDER"
fi

if [ -n "$RADARR_API_KEY" ]; then
  CMD="$CMD -Dradarr.apikey=$RADARR_API_KEY"
fi

if [ -n "$RADARR_BASE_URL" ]; then
  CMD="$CMD -Dradarr.baseUrl=$RADARR_BASE_URL"
fi

if [ -n "$RADARR_QUALITY_PROFILE" ]; then
  CMD="$CMD -Dradarr.qualityProfile=$RADARR_QUALITY_PROFILE"
fi

if [ -n "$RADARR_ROOT_FOLDER" ]; then
  CMD="$CMD -Dradarr.rootFolder=$RADARR_ROOT_FOLDER"
fi

if [ -n "$PLEX_WATCHLIST_URL_1" ]; then
  CMD="$CMD -Dplex.watchlist1=$PLEX_WATCHLIST_URL_1"
fi

if [ -n "$PLEX_WATCHLIST_URL_2" ]; then
  CMD="$CMD -Dplex.watchlist2=$PLEX_WATCHLIST_URL_2"
fi

if [ -n "$REFRESH_INTERVAL_SECONDS" ]; then
  CMD="$CMD -Dinterval.seconds=$REFRESH_INTERVAL_SECONDS"
fi

exec $CMD

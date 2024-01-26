package model

import com.github.blemale.scaffeine.Cache

trait WatchlistarrCache

case class CaffeineCache(client: Cache[ThirdPartyType, Seq[Item]]) extends WatchlistarrCache

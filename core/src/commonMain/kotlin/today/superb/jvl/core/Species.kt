package today.superb.jvl.core

import kotlinx.serialization.Serializable

/** 생명체 종. [GameState.species]가 sonar/battle 렌더의 단일 소스. 데모 `SPECIES` 1:1. */
@Serializable
enum class Species { Ghost, Blob, Jelly, Squid, Pixel }

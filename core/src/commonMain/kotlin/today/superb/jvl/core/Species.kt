package today.superb.jvl.core

import kotlinx.serialization.Serializable

/** 생명체 종. 1차 마일스톤은 Ghost만 렌더, 나머지는 후속. 데모 `SPECIES` 1:1. */
@Serializable
enum class Species { Ghost, Blob, Jelly, Squid, Pixel }

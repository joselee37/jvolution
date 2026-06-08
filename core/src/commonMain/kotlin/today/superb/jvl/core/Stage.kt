package today.superb.jvl.core

import kotlinx.serialization.Serializable

/** 생명체 성장 단계. 선언 순서가 곧 진화 순서(ordinal). 데모 `STAGES` 1:1. */
@Serializable
enum class Stage { Egg, Larva, Juvenile, Adult }

/** 다음 단계. 마지막(Adult)이면 자기 자신. */
fun Stage.next(): Stage = Stage.entries.getOrElse(ordinal + 1) { this }

/** 더 진화할 수 있는 단계인지(마지막이 아닌지). */
fun Stage.canAdvance(): Boolean = ordinal < Stage.entries.lastIndex

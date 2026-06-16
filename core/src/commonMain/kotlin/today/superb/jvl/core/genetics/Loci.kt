package today.superb.jvl.core.genetics

/**
 * 고정 좌위 카탈로그(코드 상수). 저장본은 대립유전자 값만 담고 좌위 정의는 여기서 온다.
 *
 * [ALL]은 id 오름차순 → index == [Locus.id]. 좌위 추가/제거 시 [GENOME_VERSION]을 올린다.
 */
object Loci {
    val ALL: List<Locus> = listOf(
        Locus(0, "bodyLength", Domain.APPEARANCE, Dominance.INCOMPLETE_BLEND, 1, 9),
        Locus(1, "branchAngle", Domain.APPEARANCE, Dominance.INCOMPLETE_BLEND, 0, 12),
        Locus(2, "symmetry", Domain.APPEARANCE, Dominance.COMPLETE, 1, 8),
        Locus(3, "recursionDepth", Domain.APPEARANCE, Dominance.INCOMPLETE_BLEND, 1, 6),
        Locus(4, "hue", Domain.APPEARANCE, Dominance.CODOMINANT, 0, 7),
        Locus(5, "pattern", Domain.APPEARANCE, Dominance.COMPLETE, 0, 5),
        Locus(6, "vigorA", Domain.STAT, Dominance.INCOMPLETE_BLEND, 0, 10),
        Locus(7, "vigorB", Domain.STAT, Dominance.INCOMPLETE_BLEND, 0, 10),
        Locus(8, "metabolismA", Domain.STAT, Dominance.INCOMPLETE_BLEND, 0, 10),
        Locus(9, "metabolismB", Domain.STAT, Dominance.INCOMPLETE_BLEND, 0, 10),
        Locus(10, "resilienceA", Domain.STAT, Dominance.INCOMPLETE_BLEND, 0, 10),
        Locus(11, "resilienceB", Domain.STAT, Dominance.INCOMPLETE_BLEND, 0, 10),
        Locus(12, "aggression", Domain.BEHAVIOR, Dominance.COMPLETE, 0, 10),
        Locus(13, "sociability", Domain.BEHAVIOR, Dominance.COMPLETE, 0, 10),
        Locus(14, "boldness", Domain.BEHAVIOR, Dominance.INCOMPLETE_BLEND, 0, 10),
        Locus(15, "tempo", Domain.BEHAVIOR, Dominance.INCOMPLETE_BLEND, 0, 10),
    )

    /** 좌위 개수 == 게놈 대립유전자 길이의 단일 소스. */
    val SIZE = ALL.size

    /** id로 좌위 조회. [ALL]이 id 정렬이라 index == id. */
    fun byId(id: Int): Locus = ALL[id]
}

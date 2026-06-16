package today.superb.jvl.core.genetics

/**
 * 게놈 readout helper — 순수(Compose 무관)라 터미널(:core)과 Compose(:shared)가 공유한다.
 */

/** 9단계 블록(0..8). index 0 = 공백(최저), 8 = 꽉 찬 블록. */
private const val BLOCKS = " ▁▂▃▄▅▆▇█"

/**
 * 게놈 "지문" — 앞 8좌위(외형 6 + vigor 2)의 평균 대립유전자를 좌위 범위에 맞춰 8단계 블록으로.
 * 같은 게놈은 같은 문자열(결정론). 혈통 트리·헤더의 시각적 식별자.
 */
fun genomeSignature(genome: Genome): String = (0 until 8).joinToString("") { i ->
    val locus = Loci.ALL[i]
    val pair = genome.alleles.getOrNull(i)
    val v = if (pair != null) (pair.maternal + pair.paternal) / 2 else (locus.min + locus.max) / 2
    val span = (locus.max - locus.min).coerceAtLeast(1)
    val idx = ((v - locus.min) * 8 / span).coerceIn(0, 8)
    BLOCKS[idx].toString()
}

/** 근친계수 위험 등급. 임계는 half-sib(0.125)·full-sib/parent-child(0.25). */
enum class InbreedingRisk { SAFE, CLOSE, INBRED }

/** F → 위험 등급. UI가 라벨/색으로 매핑. */
fun classifyInbreeding(f: Double): InbreedingRisk = when {
    f < 0.125 -> InbreedingRisk.SAFE
    f < 0.25 -> InbreedingRisk.CLOSE
    else -> InbreedingRisk.INBRED
}

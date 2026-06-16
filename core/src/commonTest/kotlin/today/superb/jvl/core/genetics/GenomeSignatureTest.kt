package today.superb.jvl.core.genetics

import today.superb.jvl.core.SeededRng
import kotlin.test.Test
import kotlin.test.assertEquals

class GenomeSignatureTest {

    @Test
    fun signature_is_eight_chars_and_deterministic() {
        val g = randomGenome(SeededRng(1L))
        val sig = genomeSignature(g)
        assertEquals(8, sig.length)
        assertEquals(sig, genomeSignature(g))   // 같은 게놈 → 같은 시그니처
    }

    @Test
    fun different_genomes_can_differ() {
        // 극단 게놈(전부 최소 vs 전부 최대)은 시그니처가 달라야 한다.
        val lo = Genome(alleles = Loci.ALL.map { AllelePair(it.min, it.min) })
        val hi = Genome(alleles = Loci.ALL.map { AllelePair(it.max, it.max) })
        assertEquals(8, genomeSignature(lo).length)
        kotlin.test.assertNotEquals(genomeSignature(lo), genomeSignature(hi))
    }

    @Test
    fun classify_inbreeding_thresholds() {
        assertEquals(InbreedingRisk.SAFE, classifyInbreeding(0.0))
        assertEquals(InbreedingRisk.SAFE, classifyInbreeding(0.124))
        assertEquals(InbreedingRisk.CLOSE, classifyInbreeding(0.125))   // half-sib 경계
        assertEquals(InbreedingRisk.CLOSE, classifyInbreeding(0.249))
        assertEquals(InbreedingRisk.INBRED, classifyInbreeding(0.25))   // full-sib/parent-child 경계
        assertEquals(InbreedingRisk.INBRED, classifyInbreeding(0.5))
    }
}

package today.superb.jvl.core.genetics

/**
 * Wright 근친계수(친족계수 kinship) 계산. 리서치 "출처 없음 — 직접 설계", 설계 §6.
 *
 * 표준 재귀 kinship: `f(a,b)`는 a·b에서 무작위로 뽑은 두 대립유전자가 동일 조상에서 유래해
 * 동일할(IBD) 확률. 자기 자신 또는 더 최근(높은 gen) 노드를 부모로 하강해 정의한다.
 *
 * 혈통은 DAG(부모는 항상 자식보다 gen이 작거나 같지 않음 — 더 작음)이고, 정렬된 (min,max) id
 * 쌍으로 메모이즈하므로 종료가 보장된다.
 */
object Kinship {

    /**
     * 친족계수 f(a,b). 같은 id면 `0.5*(1+F_self)`, 다르면 더 최근 노드를 부모로 하강.
     *
     * [nodes]에 없는 id는 founder(gen=-1, 부모 없음)로 간주한다.
     */
    fun coefficientOfKinship(a: String, b: String, nodes: Map<String, PedigreeNode>): Double =
        kinship(a, b, nodes, HashMap())

    /**
     * 개체 x의 근친계수 F = f(x의 부모) = f([motherId], [fatherId]).
     * 부모 한쪽이라도 null이면 0(혈통 미상 → founder로 취급).
     */
    fun inbreeding(motherId: String?, fatherId: String?, nodes: Map<String, PedigreeNode>): Double {
        if (motherId == null || fatherId == null) return 0.0
        return coefficientOfKinship(motherId, fatherId, nodes)
    }

    /** 결측 id는 founder(gen=-1, 부모 없음). */
    private fun node(id: String, nodes: Map<String, PedigreeNode>): PedigreeNode =
        nodes[id] ?: PedigreeNode(id = id, gen = -1, motherId = null, fatherId = null)

    private fun memoKey(a: String, b: String): Pair<String, String> =
        if (a <= b) a to b else b to a

    private fun kinship(
        a: String,
        b: String,
        nodes: Map<String, PedigreeNode>,
        memo: MutableMap<Pair<String, String>, Double>,
    ): Double {
        val key = memoKey(a, b)
        memo[key]?.let { return it }

        val result: Double
        if (a == b) {
            // f(a,a) = 0.5 * (1 + F_self), F_self = f(부모) 또는 0(founder).
            val self = node(a, nodes)
            val fSelf =
                if (self.motherId != null && self.fatherId != null) {
                    kinship(self.motherId, self.fatherId, nodes, memo)
                } else {
                    0.0
                }
            result = 0.5 * (1.0 + fSelf)
        } else {
            val na = node(a, nodes)
            val nb = node(b, nodes)
            // 불변식(load-bearing): 모든 부모는 자식보다 gen이 작아야 한다. gen을 "다른 쪽의 조상이
            // 아닌 노드"의 프록시로 써서 하강하기 때문 — 부모 gen >= 자식 gen인 혈통(gen 역전)이 들어오면
            // 잘못된 계수가 나온다. 현재 이 불변식은 reducer(자식 gen=부모+1)와 마이그레이션(조상=founder)이
            // 보장한다. 새 코드가 Ancestor를 기록할 때도 이 불변식을 유지할 것.
            // 더 최근(높은 gen) 노드를 하강. 동일 gen이면 부모 보유한 쪽; 둘 다 보유면 a.
            val aHasParents = na.motherId != null && na.fatherId != null
            val bHasParents = nb.motherId != null && nb.fatherId != null
            val descendA = when {
                na.gen > nb.gen -> true
                na.gen < nb.gen -> false
                aHasParents -> true       // 동일 gen: a가 부모 보유(둘 다 보유여도 a)
                bHasParents -> false      // 동일 gen: b만 부모 보유
                else -> true              // 둘 다 founder: 임의로 a (어차피 부모 없음 → 0)
            }
            val x = if (descendA) na else nb
            val other = if (descendA) b else a
            result =
                if (x.motherId != null && x.fatherId != null) {
                    0.5 * (kinship(x.motherId, other, nodes, memo) +
                        kinship(x.fatherId, other, nodes, memo))
                } else {
                    0.0  // founder a≠b → 서로 다른 founder는 무관
                }
        }

        memo[key] = result
        return result
    }
}

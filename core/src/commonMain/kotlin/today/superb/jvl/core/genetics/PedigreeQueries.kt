package today.superb.jvl.core.genetics

import today.superb.jvl.core.GameState

/**
 * 혈통 DAG 조회 — [GameState]에서 근친계수 계산용 [PedigreeNode] 맵을 합성한다(설계 §6).
 *
 * `:core` 순수 도메인에 머문다(stdlib + 기존 유전 엔진만 사용).
 */

/**
 * `ancestors` + 현재 개체 + 피어(founder, gen=0)를 합쳐 id→노드 맵을 만든다.
 *
 * - 아카이브된 조상은 자신의 부모/gen을 그대로 노드로 노출.
 * - 현재 개체는 `creatureId` → 자신의 [GameState.motherId]/[GameState.fatherId], gen=[GameState.gen].
 * - 각 피어는 `peer.id` → founder 노드(gen=0, 부모 없음).
 *
 * id 충돌 시 **조상 레코드를 우선**한다(이미 아카이브된 혈통이 권위).
 */
fun GameState.pedigree(): Map<String, PedigreeNode> {
    val nodes = LinkedHashMap<String, PedigreeNode>()

    // 피어 founder(gen=0, 부모 없음). 가장 약한 우선순위 — 뒤에서 덮어쓰일 수 있음.
    for (peer in peers) {
        nodes[peer.id] = PedigreeNode(id = peer.id, gen = 0, motherId = null, fatherId = null)
    }

    // 현재 개체.
    nodes[creatureId] = PedigreeNode(
        id = creatureId,
        gen = gen,
        motherId = motherId,
        fatherId = fatherId,
    )

    // 아카이브된 조상 — id 충돌 시 권위(마지막에 적용).
    for (a in lineage.ancestors) {
        nodes[a.id] = PedigreeNode(id = a.id, gen = a.gen, motherId = a.motherId, fatherId = a.fatherId)
    }

    return nodes
}

/**
 * 현재 개체 × [peerId] 피어 교배 시 자식의 예측 근친계수 F = f(현재개체, 피어).
 * 교배 전 경고/표시용(설계 §6).
 */
fun predictedInbreeding(state: GameState, peerId: String): Double =
    Kinship.coefficientOfKinship(state.creatureId, peerId, state.pedigree())

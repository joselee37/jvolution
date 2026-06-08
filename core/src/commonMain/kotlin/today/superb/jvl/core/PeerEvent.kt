package today.superb.jvl.core

/**
 * 처리 대기 중인 피어 요청. 데모 `pendingRequest: { from, type }` 1:1.
 *
 * 한 번에 하나만 존재한다(single-request gate) — `accept`/`decline`으로 비워야 다음 도전이 가능.
 * 현재는 [RequestType.Challenge]만 발생한다(breed는 데모에 표시 전용으로만 존재, 미발생).
 */
data class PeerRequest(val from: String, val type: RequestType)

enum class RequestType { Challenge }

/** 터미널이 자동 에코할 피어 이벤트 종류. 데모 `tagByKind` 매핑의 키 1:1. */
enum class PeerEventKind { Challenge, Friendly, Accept, Decline }

/**
 * 피어 근접 AI/요청 처리에서 발생해 **터미널이 자동 출력**할 이벤트.
 * 데모 `peerEventLatest: { kind, peerId, lines }` 1:1.
 *
 * reducer가 [GameState.peerEventNonce]를 올리며 이 값을 갱신하면, :shared의 TerminalScreen이
 * nonce 변화를 감지해 [lines]를 [kind]에 맞는 라인 종류로 history에 append한다.
 */
data class PeerEvent(val kind: PeerEventKind, val peerId: String, val lines: List<String>)

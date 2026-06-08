package today.superb.jvl.viewmodel

import today.superb.jvl.persistence.GameStore

/** 인메모리 테스트 저장소 — save 횟수/마지막 JSON을 기록. */
class FakeGameStore(initial: String? = null) : GameStore {
    var saved: String? = initial
    var saveCount = 0
        private set

    override fun load(): String? = saved

    override fun save(json: String) {
        saved = json
        saveCount++
    }
}

package today.superb.jvl.core

/**
 * 메인 베젤이 보여주는 뷰. 데모 `view: 'sonar' | 'tree' | 'radar' | 'battle'` 1:1.
 *
 * 1차에서는 항상 [Sonar]이며 전환 경로가 없다([Action.SetView]는 2차 선반영). Tree/Radar/Battle은
 * 후속 마일스톤에서 활성화된다.
 *
 * 주: `view`는 폰 전용 프레젠테이션 관심사다. MVP 단순함을 위해 GameState에 두지만
 * (워치는 무시), 폰/워치 분기가 부담이 되면 :shared UI-state로 분리한다. PLAN.md 참조.
 */
enum class View { Sonar, Tree, Radar, Battle }

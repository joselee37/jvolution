# Releasing — 로컬 빌드 + Play 내부 트랙 배포

GitHub Actions 대신 **이 서버에서 직접** 빌드/서명/배포한다(warm Gradle 데몬 + 로컬 캐시라 빠름).
Play 업로드는 AGP/Gradle 플러그인에 의존하지 않는 Play Developer API 스크립트를 쓴다
(GPP 플러그인은 AGP 9 비호환이라 사용 안 함).

| 구성 | 역할 |
|---|---|
| `scripts/release.sh` | 서명 AAB 빌드(`bundleRelease`) → Play 내부 트랙 업로드 |
| `scripts/play_upload.py` | Play Developer API 업로더(edit→upload→track→commit) |
| `scripts/.venv/` | google-api-python-client 격리 설치(gitignored) |
| `.github/workflows/android.yml` | (선택) PR 빠른 테스트 게이트 — jvmTest만. 안 쓰면 삭제 가능 |

## 실행

```sh
./scripts/release.sh 1.0.1      # versionName 인자(생략 시 release.env의 VERSION_NAME)
```

`versionCode`는 `git rev-list --count HEAD`(git 커밋 수)로 단조 증가. `versionName`은 인자
(생략 시 release.env의 `VERSION_NAME`). 특정 코드로 강제하려면 `VERSION_CODE=12345 ./scripts/release.sh 1.0.2`.
주의: 히스토리 재작성(squash/rebase)으로 커밋 수가 줄면 versionCode가 역행할 수 있다.

## 일회성 설정

### 1. 업로드 키스토어 (로컬, 1회)

```sh
keytool -genkeypair -v -keystore ~/keys/jvolution-upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias jvolution \
  -dname "CN=jvolution, O=superb.today, C=KR"
```

`.jks`는 저장소 밖(예: `~/keys/`)에 두거나 `.gitignore`된 경로에. **커밋 금지**.

### 2. `scripts/release.env` (gitignored)

```sh
# scripts/release.env — 키스토어 자격(커밋 금지)
KEYSTORE_PATH=/home/jose/keys/jvolution-upload.jks
KEYSTORE_PASSWORD=<store_pw>
KEY_ALIAS=jvolution
KEY_PASSWORD=<key_pw>
VERSION_NAME=1.0
# PLAY_JSON_KEY=scripts/play-service-account.json   # 기본 경로면 생략 가능
```

### 3. Play Console + 서비스 계정

- [Play Console](https://play.google.com/console)에서 앱 생성, 패키지명 **`today.superb.jvl`**.
- **Play App Signing** 활성화(위 키 = 업로드 키, Google이 최종 서명 관리).
- **내부 테스트(Internal testing)** 트랙 + 테스터 등록.
- 연결된 Google Cloud에서 **서비스 계정** 생성 → JSON 키 다운로드 →
  `scripts/play-service-account.json`로 저장(gitignored).
- Play Console → 사용자 및 권한 → 그 서비스 계정 초대 → **앱 릴리스 관리** 권한.

### 4. 첫 AAB는 수동 업로드 (Play 정책)

Play API는 앱에 최소 1개 AAB가 이미 올라간 뒤부터 트랙 업로드를 허용한다.
최초 1회만 `./gradlew :androidApp:bundleRelease`(release.env 환경에서)로 만든
`androidApp/build/outputs/bundle/release/androidApp-release.aab`를 Play Console에 **수동 업로드**.
이후부터 `./scripts/release.sh`가 자동 업로드.

## venv 재생성 (필요 시)

```sh
python3 -m venv scripts/.venv
scripts/.venv/bin/pip install google-api-python-client google-auth
```

## 로컬 빌드 동작

키스토어 환경변수가 없으면 `release`는 **미서명**으로 빌드된다(설정 검증용).
`release.sh`는 `release.env`를 source해 서명 환경을 채운 뒤 빌드/업로드한다.

## iOS (후속)

iOS는 macOS + Xcode + Apple 서명 + (TestFlight) App Store Connect API가 필요해 별도 추가.
이 서버(Linux)에서는 iOS 빌드 불가.

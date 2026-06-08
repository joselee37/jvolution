# Releasing — Android CI/CD

GitHub Actions로 빌드/테스트(CI)와 Play 내부 트랙 배포(CD)를 자동화한다.

| 워크플로 | 트리거 | 동작 |
|---|---|---|
| `.github/workflows/android.yml` | master push / PR | `:core` + `:shared` jvmTest → `assembleDebug` → 디버그 APK 아티팩트 |
| `.github/workflows/android-release.yml` | `v*` 태그 push / 수동 | 테스트 → **서명 AAB** → **Play 내부 트랙 업로드** |

CI(android.yml)는 외부 설정 없이 바로 동작한다. 아래는 **릴리스(Play 배포)** 에만 필요한 일회성 설정.

## 1. 업로드 키스토어 생성 (로컬, 1회)

```sh
keytool -genkeypair -v -keystore upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias jvolution -storepass <STORE_PW> -keypass <KEY_PW> \
  -dname "CN=jvolution, O=superb.today, C=KR"
```

이 `.jks`는 **절대 커밋하지 않는다**(`.gitignore`에 `*.jks` 등록됨). base64로 인코딩해 GitHub 시크릿에 넣는다:

```sh
base64 -w0 upload-keystore.jks   # 출력 전체를 ANDROID_KEYSTORE_BASE64 시크릿에
```

## 2. GitHub Secrets 등록

Repo → Settings → Secrets and variables → Actions → New repository secret:

| 시크릿 | 값 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | 위 base64 출력 |
| `KEYSTORE_PASSWORD` | 키스토어 `<STORE_PW>` |
| `KEY_ALIAS` | `jvolution` |
| `KEY_PASSWORD` | 키 `<KEY_PW>` |
| `PLAY_SERVICE_ACCOUNT_JSON` | 아래 5단계의 서비스 계정 JSON 전체 |

## 3. Play Console 앱 생성

- [Play Console](https://play.google.com/console)에서 앱 생성, **패키지명 `today.superb.jvl`**.
- **Play App Signing** 활성화(권장) — 위 키는 *업로드 키*가 되고 Google이 최종 서명을 관리.
- 테스트 → **내부 테스트(Internal testing)** 트랙 생성 + 테스터 목록 등록.

## 4. 첫 AAB는 수동 업로드 (Play 정책)

Play API는 **앱이 존재하고 최소 1개의 AAB가 이미 올라간 뒤**부터 트랙 업로드를 허용한다.
따라서 최초 1회는 `./gradlew :androidApp:bundleRelease`로 만든 AAB를
(`androidApp/build/outputs/bundle/release/androidApp-release.aab`) Play Console에 **수동 업로드**한다.
이후부터 워크플로가 자동 업로드한다.

## 5. Google Cloud 서비스 계정 (Play API 권한)

- Play Console과 연결된 Google Cloud 프로젝트에서 서비스 계정 생성 → JSON 키 다운로드.
- Play Console → 사용자 및 권한 → 그 서비스 계정 초대 → **앱 릴리스 관리** 권한 부여.
- JSON 파일 내용 전체를 `PLAY_SERVICE_ACCOUNT_JSON` 시크릿에 붙여넣는다.

## 6. 릴리스 실행

```sh
git tag v1.0.0
git push origin v1.0.0
```

`v*` 태그 push → `android-release.yml`이 테스트 → 서명 AAB 빌드 → 내부 트랙 업로드.
`versionCode`는 워크플로 실행 번호(`github.run_number`)로 단조 증가, `versionName`은 태그명(`v` 제거).
수동 실행(workflow_dispatch) 시 `versionName`을 입력으로 받는다.

## 로컬 동작

키스토어 환경변수가 없으면 `release`는 **미서명**으로 빌드된다(설정 검증용). 즉 로컬
`bundleRelease`는 미서명 AAB를 만들고, 서명은 위 환경변수가 있는 CI에서만 적용된다.

## iOS (후속)

iOS 빌드/TestFlight는 **macOS 러너**(`runs-on: macos-latest`) + Xcode + Apple 서명이 필요해
별도 워크플로로 추가한다(현재 범위 밖).

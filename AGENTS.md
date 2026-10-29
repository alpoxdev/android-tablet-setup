# AGENTS.md

이 저장소에서 작업하는 AI 에이전트가 지키는 규칙입니다.
사람을 위한 설명은 [README.md](README.md), 설치와 연결 절차는 [docs/SETUP.md](docs/SETUP.md),
설계 근거는 [docs/DESIGN.md](docs/DESIGN.md)에 있습니다.

## 저장소 한 줄

안드로이드 태블릿(샤오미 패드 12.7 프로 2025)을 맥북의 트랙패드·키보드와 원격 화면으로 쓰게 하는 Kotlin 앱입니다.
앱 이름은 PadDeck, 패키지는 `dev.paddeck`입니다.

## 빌드 환경

| 항목 | 값 |
|---|---|
| JDK | 17 (Gradle daemon toolchain도 17) |
| Android SDK | Platform 35, Build-Tools 35 |
| Gradle | 8.11.1 (wrapper 고정) |
| Kotlin / AGP | 2.0.21 / 8.7.3 |
| compileSdk / minSdk / targetSdk | 35 / 30 / 35 |

`local.properties`에 `sdk.dir`이 있어야 빌드가 됩니다. 이 파일은 기기마다 다르므로 Git에 올리지 않습니다.
`gradle/wrapper/gradle-wrapper.jar`는 바이너리지만 저장소에 포함합니다.

## 자주 쓰는 명령

```bash
./gradlew :app:assembleDebug        # APK 빌드
./gradlew :app:testDebugUnitTest    # JVM 단위 테스트
adb install -r app/build/outputs/apk/debug/app-debug.apk
tools/mac-setup.sh                  # 맥 화면 공유 상태·주소·5900 포트 확인
tools/probe-device.sh               # 태블릿 입력 장치와 매직 트랙패드 인식 확인
```

## 코드 지도

```
app/src/main/java/dev/paddeck/
├── MainActivity.kt   앱 진입, 연결·원격·설정 화면 전환
├── SessionBus.kt     원격 세션과 IME 서비스를 잇는 전역 지점
├── data/Profile.kt   연결 프로필과 설정 저장(SharedPreferences)
├── ime/              PadKeyboardService, PadKeyboardView, HangulComposer
├── input/            Keysym, RemoteInput, TrackpadEngine
├── ui/               ConnectScreen, RemoteScreen, SettingsScreen, Theme
└── vnc/              Protocol, RfbClient, Decoders, Des
```

테스트는 `app/src/test/java/dev/paddeck/` 아래 같은 패키지 구조를 따릅니다.

## 경계 규칙

- `vnc`, `input`, `ime`의 계산 부분은 안드로이드 의존성 없이 순수 Kotlin으로 유지합니다.
  그래야 JVM 단위 테스트로 검증할 수 있습니다. Android API는 `ui`, `MainActivity`, `ime` 서비스 진입점에서만 씁니다.
- 프로토콜 상수와 픽셀 변환은 `vnc/Protocol.kt`, 압축 해제는 `vnc/Decoders.kt`, 소켓과 상태 흐름은 `vnc/RfbClient.kt`에 둡니다.
  새 인코딩이나 메시지 타입을 넣을 때 `RfbClient`에 디코딩 코드를 복사하지 말고 `Decoders`에 추가합니다.
- 화면과 IME는 `SessionBus`로만 연결합니다. 새 전역 상태를 만들기 전에 이 객체를 확장할 수 있는지 먼저 봅니다.

## 프로토콜 사실 (RFB)

- 버전 3.8, 보안 타입 1(None)과 2(VNC DES)를 협상합니다.
- 인코딩은 Raw, CopyRect, Hextile, ZRLE와 가상 인코딩 DesktopSize(-223)를 받습니다.
- 픽셀은 32bpp truecolor, red-shift 16 / green 8 / blue 0이고 프레임버퍼는 ARGB `IntArray`입니다.
- 프로토콜을 고칠 때는 실제 소켓을 쓰는 `RfbClientTest`의 가짜 서버를 함께 갱신합니다.

## 입력 규칙

- 한글: 원격 연결 중에는 라틴 keysym을 보내고 조합은 맥 입력 소스가 합니다.
  로컬 키보드로 칠 때만 `HangulComposer`가 두벌식으로 조합합니다.
- 트랙패드: 탭은 왼쪽 클릭, 두 손가락 탭은 오른쪽 클릭, 두 손가락 끌기는 스크롤,
  세 손가락 스와이프는 `⌃↑ ⌃↓ ⌃← ⌃→`(Mission Control, 데스크톱 전환)입니다.
- 앱이 포인터를 직접 해석하는 곳은 마우스 잡기를 켠 원격 화면뿐입니다. 안드로이드 홈 화면과 다른 앱에서는
  운영체제가 포인터를 소유하므로 앱이 개입할 수 없습니다. 이 한계를 코드로 우회하려 하지 않습니다.

## 테스트 규칙

- 변경 뒤에는 `./gradlew :app:testDebugUnitTest`를 실제로 돌리고 결과를 확인합니다.
  UI·리소스·매니페스트를 건드렸으면 `./gradlew :app:assembleDebug`까지 돌립니다.
- 비동기 확인은 `CountDownLatch`처럼 사건을 기다리는 방식으로 씁니다. `Thread.sleep`으로 타이밍을 맞추지 않습니다.
- 실패하는 테스트를 지우거나 건너뛰어 초록색을 만들지 않습니다.
- 실기기에서만 확인되는 동작은 `tools/probe-device.sh` 출력처럼 재현 가능한 증거를 남깁니다.

## 문서 규칙

- 문서는 한국어로 씁니다. 명령, 경로, 클래스 이름, 프로토콜 이름은 원문을 유지합니다.
- 설치 절차를 바꾸면 `docs/SETUP.md`, 동작 원리를 바꾸면 `docs/DESIGN.md`, 빌드·구조를 바꾸면 `README.md`도 함께 고칩니다.
- 문장은 마침표로 끝내고, 원인과 결과를 구체적으로 적습니다.

## Git 규칙

- 올리지 않는 것: `.omo/`, `.gradle/`, `.kotlin/`, `build/`, `local.properties`, `*.keystore`, `keystore.properties`, `.env`.
  이미 `.gitignore`에 들어 있으니 예외를 만들지 않습니다.
- 커밋 메시지는 `<영역>: <요약>` 형태로 씁니다. 예: `vnc: ZRLE 팔레트 캐시 수정`, `ime: 한글 조합기 백스페이스 처리`.
- 한 커밋에는 한 가지 변경만 담고, 커밋 전에 테스트 결과를 확인합니다.

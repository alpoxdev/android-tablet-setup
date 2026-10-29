# PadDeck

[![Android CI](https://github.com/alpoxdev/android-tablet-setup/actions/workflows/android.yml/badge.svg)](https://github.com/alpoxdev/android-tablet-setup/actions/workflows/android.yml)

안드로이드 태블릿을 맥북의 트랙패드·키보드처럼 쓰는 앱입니다.
세 가지 문제를 한 앱에서 다룹니다.

1. 화면 키보드가 눌러지기 힘든 문제 → 숫자 줄과 수정 키가 있는 노트북형 한글 키보드(PadDeck 키보드)
2. 애플 트랙패드가 아이패드처럼 굴러주지 않는 문제 → 앱이 입력을 직접 받아 다시 해석하는 트랙패드 엔진
3. 기본 VNC가 뻣뻣한 문제 → 직접 만든 VNC(RFB 3.8) 클라이언트와 두 손가락 스크롤·오른쪽 클릭·세 손가락 제스처

태블릿과 맥을 같은 네트워크에 두고, 앱에서 맥 주소와 화면 공유 암호를 넣으면 바로 붙습니다.
맥에 따로 프로그램을 설치하지 않고 macOS 기본 화면 공유 서버를 씁니다.

## 요구 사항

| 항목 | 버전 |
|---|---|
| JDK | 17 |
| Android SDK | Platform 35, Build-Tools 35 |
| Gradle | 8.11.1 (wrapper가 자동으로 받습니다) |
| Kotlin / AGP | 2.0.21 / 8.7.3 |
| 안드로이드 기기 | Android 11(API 30) 이상, 가로 화면 권장 |

Android SDK 경로는 `local.properties`의 `sdk.dir`이나 `ANDROID_HOME` 환경 변수로 지정합니다.
두 파일 모두 기기마다 다른 값이라 Git에 올리지 않습니다.

## 빌드와 설치

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 테스트

```bash
./gradlew :app:testDebugUnitTest
```

VNC 클라이언트는 가짜 RFB 서버를 띄워 인증, 화면 수신, 클릭·키 전송을 실제 소켓으로 확인합니다.
한글 조합기와 트랙패드 엔진은 입력을 넣고 나온 이벤트로 확인합니다. 실기기 없이 JVM에서 돕니다.

## 구성

```
app/src/main/java/dev/paddeck/
├── MainActivity.kt   앱 진입 화면과 화면 전환
├── SessionBus.kt     원격 세션과 입력기 서비스를 잇는 지점
├── data/             연결 프로필과 설정 저장
├── ime/              PadDeck 키보드, 두벌식 한글 조합기
├── input/            키심, 포인터 이벤트, 트랙패드 이동·가속·제스처 계산
├── ui/               Compose 화면(연결, 원격, 설정, 테마)
└── vnc/              RFB 3.8 클라이언트, 프로토콜 상수, DES, 디코더
```

| 부분 | 하는 일 |
|---|---|
| `vnc` | RFB 3.8 클라이언트. VNC 인증, Raw/Hextile/ZRLE 압축 해제, 화면 크기 변경 대응 |
| `input` | 트랙패드 이동·가속·스크롤·드래그 잠금 계산 |
| `ime` | 노트북형 키보드와 두벌식 한글 조합기 |
| `ui` | 연결 화면, 원격 화면, 설정 |

`vnc`, `input`, `ime`의 조합기는 안드로이드 의존성 없이 순수 Kotlin으로 두어 JVM 테스트로 검증합니다.

## 진단 스크립트

```bash
tools/mac-setup.sh     # 맥 화면 공유 상태, 주소, 5900 포트 확인
tools/probe-device.sh  # 연결된 태블릿의 입력 장치와 매직 트랙패드 인식 확인
```

## 더 보기

- [설치와 연결 절차](docs/SETUP.md)
- [세 가지 문제와 이 앱이 하는 일](docs/DESIGN.md)
- [에이전트 작업 규칙](AGENTS.md)

## 라이선스

아직 정하지 않았습니다.

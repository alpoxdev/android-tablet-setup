#!/bin/bash
# 연결된 안드로이드 기기의 입력 장치를 확인합니다.
set -u

if ! command -v adb >/dev/null 2>&1; then
  echo "adb가 없습니다. Android SDK의 platform-tools를 PATH에 넣으세요."
  exit 1
fi

if [ "$(adb devices | grep -c 'device$')" -eq 0 ]; then
  echo "연결된 기기가 없습니다. USB 디버깅을 켜고 케이블을 연결하세요."
  exit 1
fi

echo "== 기기 정보 =="
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release

echo
echo "== 입력 장치 목록 =="
adb shell getevent -pl 2>/dev/null | grep -E "^add device|name:|INPUT_PROP" | head -60

echo
echo "== 매직 트랙패드 흔적 =="
if adb shell getevent -pl 2>/dev/null | grep -qi "magic\|trackpad"; then
  echo "트랙패드가 입력 장치로 잡혔습니다."
else
  echo "트랙패드가 보이지 않습니다. 커널에 hid-magicmouse 드라이버가 없을 가능성이 큽니다."
fi

echo
echo "== 마우스 관련 설정 =="
adb shell settings get system pointer_speed

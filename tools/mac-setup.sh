#!/bin/bash
# 맥에서 화면 공유 상태와 접속 주소를 확인합니다. 시스템 설정은 바꾸지 않습니다.
set -u

echo "== 화면 공유 상태 =="
if launchctl print system/com.apple.screensharing >/dev/null 2>&1; then
  echo "켜져 있습니다 (포트 5900)"
else
  echo "꺼져 있습니다."
  echo "시스템 설정 → 일반 → 공유 → 화면 공유를 켜세요."
fi

echo
echo "== 이 맥의 주소 =="
for iface in en0 en1; do
  ip=$(ipconfig getifaddr "$iface" 2>/dev/null)
  if [ -n "$ip" ]; then
    echo "$iface: $ip"
  fi
done

echo
echo "== VNC 암호 설정 위치 =="
echo "시스템 설정 → 일반 → 공유 → 화면 공유 ⓘ → 컴퓨터 설정"
echo "\"VNC 사용자가 암호로 제어하도록 허용\"을 켜고 암호를 정하세요."

echo
echo "== 포트 확인 =="
if nc -z 127.0.0.1 5900 2>/dev/null; then
  echo "127.0.0.1:5900 응답 있음"
else
  echo "127.0.0.1:5900 응답 없음 (화면 공유가 꺼져 있을 수 있습니다)"
fi

# 메시지 명세 초안

기준: `development-rules.pdf` (2026-11-05). 이 문서는 원본을 개발 중에 보기 쉽게 옮긴 요약이며, 미정 사항은 확정된 규칙으로 취급하지 않습니다.

## 전송 형식

```text
[4바이트 big-endian int: UTF-8 본문의 바이트 길이][TYPE|roomId|userId|본문...]
```

- `DataOutputStream.writeInt`, `write`, `flush` 순으로 보냅니다.
- `DataInputStream`으로 길이를 읽고 `readFully`로 본문 전체를 읽습니다.
- 길이는 글자 수가 아니라 UTF-8 바이트 수입니다. TCP의 read 한 번을 메시지 하나로 간주하지 않습니다.
- 공통 헤더 세 필드 뒤의 본문은 그대로 유지합니다. 본문을 해석할 때는 메시지별 필드 수로 `split("[|]", n)`을 제한합니다.
- 목록 구분은 `,`, 항목 안의 값 구분은 `:`입니다. 자유 텍스트는 마지막 필드에 둡니다.
- 서버는 클라이언트가 주장한 roomId·userId를 신뢰하지 않고 소켓의 세션 정보를 사용합니다.
- 서버가 보내는 방 공통 메시지의 userId는 `0`, 로비의 roomId는 `0`입니다.
- 기본 코드의 프레임 상한은 64 KiB입니다. 게임 규칙과 별개인 수신 길이 검사이며 변경 시 양쪽 구현을 함께 확인합니다.

## 클라이언트 → 서버

| TYPE | 본문 | 의미 |
|---|---|---|
| LOGIN | 닉네임 | 첫 메시지 |
| ROOM_LIST | 없음 | 방 목록 요청 |
| ROOM_CREATE | 방 제목 | 방 생성 후 방장으로 입장 |
| ROOM_JOIN | 입장할 roomId | 방 입장 |
| ROOM_LEAVE | 없음 | 방 퇴장; 게임 중이면 탈락 |
| READY | true / false | 준비 상태 |
| START | 없음 | 방장 요청, 전원 준비 후 시작 |
| INPUT | UP / DOWN / LEFT / RIGHT / STOP | 키를 누르거나 뗄 때 방향 전달; 좌표 전송 없음 |
| BOMB | 없음 | 현재 위치에 물폭탄 설치 |
| CHAT | 메시지 | 같은 방 채팅 |
| EMOJI | 이모티콘 번호 | 같은 방 이모티콘 |

## 서버 → 클라이언트

표의 ` / `는 별도 `|` 필드를 뜻합니다.

| TYPE | 본문 | 의미 |
|---|---|---|
| LOGIN_OK | userId | ID 배정 |
| ERROR | 코드:메시지 | 요청 거절 |
| ROOM_LIST | roomId:제목:인원:상태,... | 상태 WAIT / PLAYING |
| ROOM_INFO | 방장ID / userId:닉네임:준비,... | 참가자·준비 상태 |
| GAME_INIT | 맵 문자열 / userId:x:y,... | `.` 빈칸, `#` 벽, `B` 상자 |
| STATE | tick / id:x:y:상태,... / col:row:남은틱,... | 매 틱의 플레이어·폭탄 상태 |
| EVENT | 종류:값... | 폭발, 상자, 아이템, 피격, 탈락 |
| CHAT | userId:닉네임 / 메시지 | 채팅 중계 |
| EMOJI | userId:이모티콘번호 | 이모티콘 중계 |
| PLAYER_LEFT | userId | 접속 종료·퇴장 |
| RESULT | WIN:userId 또는 DRAW:userId,... | 결과 후 대기 상태 복귀 |

```text
INPUT|3|2|LEFT
STATE|3|0|418|1:200:120:ALIVE,2:360:80:TRAPPED|5:3:12
EVENT|3|0|EXPLODE:5:3:2:1:2:0
CHAT|3|2|안녕 | 반가워
```

EVENT 본문:

```text
EXPLODE:col:row:상:하:좌:우
BOX_BREAK:col:row:아이템
ITEM_GET:userId:col:row:종류
TRAPPED:userId
DEAD:userId
```

## 서버 기준값

포트 5000, 맵 15×13 타일, 타일 40px, 틱 50ms(20Hz), 방 2~4명.
위치는 픽셀 정수 `(x, y)`, 맵은 타일 `(col, row)`를 사용합니다.
폭탄은 플레이어 중심 타일에 설치하고 60틱 후 폭발하며, 기본 위력 1칸·설치 개수 1개입니다.
물방울에 갇힌 뒤 100틱 후 탈락합니다. 같은 틱에 마지막 생존자들이 모두 탈락하면 무승부입니다.
맵 전체는 GAME_INIT으로 한 번 보내고, 이후 변화는 EVENT로 전달합니다.

## 기본 코드 범위

현재 서버는 LOGIN과 ROOM_LIST만 처리합니다. 미구현 요청은 `ERROR`로 응답합니다.
`NOT_LOGGED_IN`, `ALREADY_LOGGED_IN`, `INVALID_NICK`, `NOT_IMPLEMENTED`는 접속 확인용 코드이며 최종 에러 목록은 팀 회의에서 확정합니다.
방·게임 큐·GameLoop·UI·닉네임 중복 검사는 각 담당자가 구현합니다.

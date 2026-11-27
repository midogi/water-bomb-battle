# 메시지 명세 초안

## 대기실 캐릭터와 맵 선택

- 입장 시 캐릭터는 `NONE`, 준비 상태는 준비 전이다. 준비한 참가자는 준비를 취소한 뒤 캐릭터를 바꿀 수 있다. 이미 선택된 캐릭터를 요청하면 `CHARACTER_TAKEN`을 보내고 기존 선택을 유지한다.
- 방장은 대기 중에만 `MAP_SELECT`로 맵을 바꿀 수 있다. 새 방의 기본 맵은 `TIDE`이며, 맵 코드는 `common.model.ArenaMap`과 같다.
- 선택이 바뀌면 같은 방에 `ROOM_INFO`를 보낸다. 참가자 항목의 네 번째 필드는 캐릭터 코드이고, 마지막 필드는 맵 코드다.

```text
ROOM_INFO|3|0|2|1:물방울:false:CRAB,2:파랑:true:TURTLE|FROST
```

- 방장은 2~4명 전원이 캐릭터를 선택하고 방장 외 전원이 준비했을 때 시작할 수 있다. 서버는 확정된 `ROOM_INFO`를 먼저 보내고, 선택한 맵의 배치와 참가자 좌표를 `GAME_INIT`으로 전달한다.

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
| LOBBY_WATCH | 없음 | 로그인 후 로비 갱신 구독. 방에서 돌아와도 같은 연결의 구독 유지 |
| ROOM_LIST | 없음 | 방 목록 요청 |
| ROOM_CREATE | 방 제목 | 방 생성 후 방장으로 입장 |
| ROOM_JOIN | 입장할 roomId | 방 입장 |
| ROOM_LEAVE | 없음 | 방 퇴장; 게임 중이면 탈락 |
| CHARACTER_SELECT | NONE / MUDSKIPPER / CRAB / PLOVER / TURTLE | 캐릭터 선택 또는 해제 |
| MAP_SELECT | RANDOM 또는 맵 코드 | 방장이 대기 중에 맵 선택 |
| READY | true / false | 준비 상태 |
| START | 없음 | 방장 요청, 전원 캐릭터 선택 및 방장 외 전원 준비 후 시작 |
| INPUT | UP / DOWN / LEFT / RIGHT / STOP | 키를 누르거나 뗄 때 방향 전달; 좌표 전송 없음 |
| BOMB | 없음 | 현재 위치에 물폭탄 설치 |
| ITEM_USE | INVISIBILITY / LENS / NEEDLE / DART | 보유 아이템 사용 요청 |
| LOBBY_CHAT | 메시지 | 로비 채팅 (방 번호 0) |
| LOBBY_EMOJI | 이모티콘 번호 | 로비 이모티콘 (방 번호 0) |
| CHAT | 메시지 | 같은 방 채팅 |
| EMOJI | 이모티콘 번호 | 같은 방 이모티콘 |

## 서버 → 클라이언트

표의 ` / `는 별도 `|` 필드를 뜻합니다.

| TYPE | 본문 | 의미 |
|---|---|---|
| LOGIN_OK | userId | ID 배정 |
| ERROR | 코드:메시지 | 요청 거절 |
| LOBBY_UPDATE | 닉네임,... / 방 목록 | 로비에 있는 구독자에게 현재 로비 명단과 ROOM_LIST 형식의 전체 목록 전송. 비어 있는 목록은 빈 필드 |
| ROOM_LIST | roomId:제목:인원:상태,... | 상태 WAIT / PLAYING |
| ROOM_INFO | 방장ID / userId:닉네임:준비:캐릭터,... / 맵코드 | 참가자, 준비·캐릭터 상태, 선택한 맵 |
| GAME_INIT | 맵 문자열 / userId:x:y,... | `.` 빈칸, `#` 벽, `B` 상자 |
| STATE | tick / id:x:y:상태:시선방향,... / col:row:남은틱,... | 매 틱의 플레이어·폭탄 상태 |
| ITEM_STATE | col:row:종류,... / 종류:수량,... / 투명화 / 렌즈 | 바닥 아이템과 수신자 본인의 소지 수량·효과 |
| EVENT | 종류:값... | 폭발, 상자, 아이템, 피격, 탈락 |
| LOBBY_CHAT | userId:닉네임 / 메시지 | 로비에 있는 세션에만 중계 |
| LOBBY_EMOJI | userId:이모티콘번호:닉네임 | 로비에 있는 세션에만 중계 |
| CHAT | userId:닉네임 / 메시지 | 채팅 중계 |
| EMOJI | userId:이모티콘번호 | 이모티콘 중계 |
| PLAYER_LEFT | userId | 접속 종료·퇴장 |
| RESULT | WIN:userId 또는 DRAW:userId,... | 결과 후 대기 상태 복귀 |

```text
INPUT|3|2|LEFT
STATE|3|0|418|1:200:120:ALIVE:LEFT,2:360:80:TRAPPED:DOWN|5:3:12
EVENT|3|0|EXPLODE:5:3:2:1:2:0
CHAT|3|2|안녕 | 반가워
```

EVENT 본문:

```text
EXPLODE:col:row:상:하:좌:우
BOX_BREAK:col:row:아이템
ITEM_GET:userId:col:row:종류
ITEM_USED:userId:종류
DART_SHOT:userId:도착col:도착row:결과(BOMB/BLOCK/MISS):방향
TRAPPED:userId
DEAD:userId
```

`ITEM_STATE`는 수신자의 사용자 ID를 헤더에 담아 경기 시작과 내용이 바뀔 때 보냅니다. 빈 목록은 빈 필드, 시야 효과는 `true` 또는 `false`로 표시합니다. `STATE`는 수신자에게 보이는 플레이어 좌표를 넣습니다. 투명화한 플레이어의 좌표는 본인과 렌즈가 켜진 상대에게 전달합니다. 아이템이 없거나 효과를 적용할 조건이 맞지 않으면 현재 소지 수량을 유지합니다.

`STATE`의 시선 방향은 `UP`, `DOWN`, `LEFT`, `RIGHT` 중 하나입니다. 벽에 막혀도 방향 입력은 시선에 반영되며, 다트는 같은 방향으로 발사됩니다. 게임 이벤트 뒤에는 같은 틱의 `STATE`를 보내므로 화면에서 폭발과 피격을 함께 표시할 수 있습니다. 기존 방향 필드가 없는 `STATE`도 클라이언트에서 읽을 수 있습니다.

## 서버 기준값

포트 5000, 맵 15×13 타일, 타일 40px, 틱 50ms(20Hz), 방 2~4명.
위치는 픽셀 정수 `(x, y)`, 맵은 타일 `(col, row)`를 사용합니다.
폭탄은 플레이어 중심 타일에 설치하고 60틱 후 폭발하며, 기본 위력 1칸·설치 개수 1개입니다. 기본 이동 속도는 4px/틱이며 모든 캐릭터가 같은 값을 씁니다.
물방울에 갇힌 플레이어는 움직일 수 있는 다른 플레이어와 판정 사각형이 겹치면 그 틱에 즉시 탈락합니다. 접촉이 없으면 갇힌 뒤 100틱 후 탈락합니다. 두 경우 모두 `DEAD:userId`를 보냅니다. 같은 틱에 마지막 생존자들이 모두 탈락하면 무승부입니다.
새 방의 맵 선택값은 RANDOM이다. 서버는 매 경기 시작 시 실제 맵을 같은 확률로 하나 뽑는다. 방장이 특정 맵을 선택하면 해당 배치를 사용한다.
맵 전체는 GAME_INIT으로 한 번 보내고, 이후 변화는 EVENT로 전달합니다.

## 기본 코드 범위

서버는 로그인, 로비, 캐릭터·맵 선택, 준비·경기 시작, 이동·물폭탄, 아이템 사용, 채팅·이모티콘을 처리합니다. 거절 응답의 코드는 `common.protocol.ErrorCode`에서 정의합니다.

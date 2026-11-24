# 서버 게임 로직 연결 방법

게임 판정 코드는 `src/server/game/`에 있습니다. 맵 배치와 아이템 코드는 `common/model/`에서 서버와 화면이 같이 읽습니다.
실제 접속에서는 방장이 고른 맵과 방 참가자 목록으로 게임 루프를 시작합니다. 아이템 사용은 두 창 미리보기에서 확인할 수 있습니다.

## 구현 범위

- 15×13 맵, 픽셀 단위 이동, 벽·상자·폭탄 충돌
- 방별 50ms GameLoop, ConcurrentLinkedQueue 입력 처리
- 중심 타일 물폭탄 설치, 기본 설치 개수 제한, 60틱 타이머
- 십자 폭발 범위, 상자 파괴, 연쇄 폭발
- 피격 후 갇힘, 접촉 즉시 탈락 또는 100틱 후 탈락, 동시 탈락 시 무승부
- 게임 중 퇴장, 최종 승패 판정, 불변 상태 스냅샷
- 기존 GAME_INIT / STATE / EVENT / PLAYER_LEFT / RESULT 메시지 생성
- 해안 맵 9종과 2~4인 시작 위치 배정
- 상자 아이템 드롭·획득, 투명화·렌즈·바늘·다트 사용

## 네트워크 담당이 연결할 위치

1. 방장이 시작을 요청하면 로비에서 인원·준비 상태를 검사합니다.
2. 선택한 맵과 방 참가자 ID로 GameState와 GameLoop를 생성합니다.
3. INPUT과 BOMB 수신 시 **소켓 세션의 ID**와 메시지를 submit에 전달합니다.
4. 게임 중 퇴장·소켓 종료 시 playerLeft를 호출합니다.
5. 결과가 나오면 로비에서 방을 WAIT 상태로 돌리고 준비 상태를 초기화합니다.
6. 방 제거·서버 종료 시 close를 호출합니다.

```java
// 예시의 변수와 메서드는 네트워크 담당의 실제 Room API에 맞춰 연결합니다.
// movementPixelsPerTick은 팀에서 확정한 값을 전달합니다. 데모와 테스트만 4를 사용합니다.
GameState state = GameState.arena(roomId, selectedMap, participantIds, movementPixelsPerTick);
GameLoop game = new GameLoop(state, message -> {
    // 해당 방의 송신 큐에 적재합니다. 여기에서 오래 걸리는 소켓 쓰기를 하지 않습니다.
    enqueueRoomBroadcast(message);
    if (message.type() == MsgType.RESULT) {
        enqueueReturnToWaitingRoom(roomId);
    }
});
game.start();

// ClientHandler: request의 roomId/userId 대신 소켓 세션에 속한 방과 ID로 라우팅합니다.
boolean accepted = game.submit(sessionUserId, request);
// false: 없는 참가자, 종료된 게임, 또는 가득 찬 입력 큐. 로비 쪽에서 적절히 처리합니다.
// 잘못된 타입·방향·본문은 IllegalArgumentException이므로 네트워크 경계에서 잡아 ERROR로 응답합니다.

// disconnect / ROOM_LEAVE: 같은 퇴장을 중복 방송하지 않도록 처리 주체를 나눕니다.
game.playerLeft(sessionUserId);
```

ROOM_CREATE, ROOM_JOIN, READY, START, CHAT, EMOJI는 로비·네트워크 담당 범위입니다.
GameLoop는 GAME_INIT을 시작 시 한 번 보내고 이후 매 틱의 STATE와 변화 EVENT를 생성합니다.
한 틱에서 입력, 이동, 아이템 사용, 설치, 폭발·연쇄, 갇힘·탈락, 아이템 획득, STATE, RESULT 순서로 처리합니다.
폭탄을 설치한 틱이 t이면 t+60에 폭발합니다. 폭발을 반영한 뒤, 갇힌 플레이어의 40×40 판정 사각형이 ALIVE 플레이어와 겹치면 같은 틱에 `DEAD`로 바꿉니다. 두 사각형의 테두리만 맞닿아 있으면 탈락하지 않습니다. 접촉이 없으면 갇힌 틱부터 100틱 뒤에 탈락합니다.
같은 틱의 모든 탈락을 적용한 뒤 Judge를 한 번 호출합니다.

## 맵과 아이템 규칙

`ArenaMap`의 배치가 대기실 미리보기와 `GAME_INIT`에 공통으로 쓰입니다. 선택 맵에서 두 명이 시작하면 대각선 모서리에 배치합니다. 세 명 이상은 참가 순서에 따라 모서리를 사용합니다.

상자 하나당 50% 확률로 아이템이 하나 나옵니다. 네 종류의 확률은 같습니다. 캐릭터의 중심이 아이템 칸에 들어오면 획득하며, 여러 명이 같은 틱에 들어오면 참가 순서상 앞선 사람이 가져갑니다.

| 아이템 | 효과 |
|---|---|
| `INVISIBILITY` | 5초 동안 움직일 수 있는 상태라면 상대 화면에서 자신의 캐릭터와 이름을 숨깁니다. 물줄기의 피격 판정은 적용됩니다. |
| `LENS` | 5초 동안 내 화면에 투명화한 상대도 표시합니다. |
| `NEEDLE` | 물방울에 갇혔을 때 사용하면 즉시 탈출합니다. |
| `DART` | 마지막으로 바라본 방향의 최대 5칸 안에서 첫 물폭탄을 바로 터뜨립니다. 벽과 상자에 막히며, 맞힐 폭탄이 없으면 소모하지 않습니다. |

상자 파괴 시 `BOX_BREAK`에 아이템 코드 또는 `NONE`을 보냅니다. 획득 시 `ITEM_GET`, 소모 시 `ITEM_USED:userId:종류`가 나옵니다. 미리보기는 `GameLoop.useItem`으로 사용을 요청하고, 매 틱 스냅샷에서 소지 수량과 표시 여부를 읽습니다.

## 통합 전 확인할 가정

아래는 미정 내용을 고정 규칙으로 확정한 것이 아니라 현재 모듈의 가정입니다.

- 플레이어 x,y는 40×40 사각형의 좌상단입니다. 설치 타일은 중심 좌표로 계산합니다.
- 이동 속도는 생성자 인자로 받습니다. 데모·테스트의 4px/틱은 개발용 값입니다.
- 폭발 피격은 폭발한 틱에만 판정합니다. 폭발 지속 시간은 추가 합의가 필요합니다.
- TRAPPED는 DEAD 전까지 생존 인원에 포함합니다. 갇힌 마지막 생존자 처리 방식은 통합 때 확인합니다.
- 벽·상자·폭탄이 이동을 막습니다. 플레이어끼리는 지나갈 수 있으며, 갇힌 플레이어와 겹칠 때 탈락 판정이 발생합니다.
- 시작 때 겹치지 않은 위치를 배정하며, 재시작할 때는 새 GameState/GameLoop를 생성합니다.

## 검증과 시연

```powershell
./scripts/test.ps1
java -cp out server.game.GameDemo
java -cp out client.ui.WaitingRoomPreview
```

macOS / Linux / Git Bash에서는 `bash scripts/test.sh`로 같은 검증을 실행할 수 있습니다.
GameDemo는 소켓 없이 4명 상태를 만들고 한 명이 폭탄에 갇혔다 탈락하는 과정을 출력합니다.
실제 네 대 또는 네 클라이언트가 같은 방에 접속한 통합 테스트를 대신하지 않습니다.

검증에는 기존 프로토콜 테스트, 맵 9종의 2·3·4인 시작, 아이템 동시 획득, 효과 만료, 갇힘 탈출, 다트 차단과 조기 폭발이 포함됩니다.
사용 환경: JDK 22에서 `javac --release 17`로 빌드 및 실행. JDK 17 런타임 자체에서의 실행은 추후 확인합니다.

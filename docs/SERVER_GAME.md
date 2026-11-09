# 서버 게임 로직 연결 방법

게임 담당 작업은 `src/server/game/`에 모았습니다. 기존 `common/`과 네트워크·로비 코드는 수정하지 않았습니다.
이번 모듈은 `network/dev`의 로비·세션 구현에 연결하기 전 단계입니다. 기존 `ServerMain`에서 게임이 실행되는 상태는 아닙니다.

## 구현 범위

- 15×13 맵, 픽셀 단위 이동, 벽·상자·폭탄 충돌
- 방별 50ms GameLoop, ConcurrentLinkedQueue 입력 처리
- 중심 타일 물폭탄 설치, 기본 설치 개수 제한, 60틱 타이머
- 십자 폭발 범위, 상자 파괴, 연쇄 폭발
- 피격 후 갇힘, 100틱 후 탈락, 동시 탈락 시 무승부
- 게임 중 퇴장, 최종 승패 판정, 불변 상태 스냅샷
- 기존 GAME_INIT / STATE / EVENT / PLAYER_LEFT / RESULT 메시지 생성

아이템 드롭·획득, 접촉 탈락·구출, 게임 UI, 실소켓과의 통합은 미구현입니다.

## 네트워크 담당이 연결할 위치

1. 방장이 시작을 요청하면 로비에서 인원·준비 상태를 검사합니다.
2. 해당 방 참가자 ID로 GameState와 GameLoop를 생성합니다.
3. INPUT과 BOMB 수신 시 **소켓 세션의 ID**와 메시지를 submit에 전달합니다.
4. 게임 중 퇴장·소켓 종료 시 playerLeft를 호출합니다.
5. 결과가 나오면 로비에서 방을 WAIT 상태로 돌리고 준비 상태를 초기화합니다.
6. 방 제거·서버 종료 시 close를 호출합니다.

```java
// 예시의 변수와 메서드는 네트워크 담당의 실제 Room API에 맞춰 연결합니다.
// movementPixelsPerTick은 팀에서 확정한 값을 전달합니다. 데모와 테스트만 4를 사용합니다.
GameState state = GameState.arena(roomId, participantIds, movementPixelsPerTick);
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
한 틱에서 입력, 이동, 설치, 폭발·연쇄, 갇힘·탈락, STATE, RESULT 순서로 처리합니다.
폭탄을 설치한 틱이 t이면 t+60에 폭발하며, t에 갇히면 t+100에 탈락합니다.
같은 틱의 모든 탈락을 적용한 뒤 Judge를 한 번 호출합니다.

## 통합 전 확인할 가정

아래는 미정 내용을 고정 규칙으로 확정한 것이 아니라 현재 모듈의 가정입니다.

- 플레이어 x,y는 40×40 사각형의 좌상단입니다. 설치 타일은 중심 좌표로 계산합니다.
- 이동 속도는 생성자 인자로 받습니다. 데모·테스트의 4px/틱은 개발용 값입니다.
- 폭발 피격은 폭발한 틱에만 판정합니다. 폭발 지속 시간과 접촉 탈락·구출은 추가 합의가 필요합니다.
- 상자 파괴 이벤트의 `NONE`은 무드롭 임시 값입니다. 아이템 코드·드롭 확률을 정한 뒤 교체합니다. `ItemType`은 추가하지 않았습니다.
- TRAPPED는 DEAD 전까지 생존 인원에 포함합니다. 갇힌 마지막 생존자 처리 방식은 통합 때 확인합니다.
- 플레이어 간 충돌은 아직 없습니다. 벽·상자·폭탄에 대해서만 충돌합니다.
- 시작 때 겹치지 않은 위치를 배정하며, 재시작할 때는 새 GameState/GameLoop를 생성합니다.

## 검증과 시연

```powershell
./scripts/test.ps1
java -cp out server.game.GameDemo
```

macOS / Linux / Git Bash에서는 `bash scripts/test.sh`로 같은 검증을 실행할 수 있습니다.
GameDemo는 소켓 없이 4명 상태를 만들고 한 명이 폭탄에 갇혔다 탈락하는 과정을 출력합니다.
실제 네 대 또는 네 클라이언트가 같은 방에 접속한 통합 테스트를 대신하지 않습니다.

검증: 기존 프로토콜 테스트와 GameLogicTest의 18개 시나리오 통과.
사용 환경: JDK 22에서 `javac --release 17`로 빌드 및 실행. JDK 17 런타임 자체에서의 실행은 추후 확인합니다.

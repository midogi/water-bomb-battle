# 연결고리 | 물폭탄 대전

Java 소켓 통신으로 만드는 2~4인 실시간 물폭탄 대전 게임입니다.
같은 방에 접속한 플레이어가 맵을 이동하며 물폭탄을 설치하고, 폭발을 피해 마지막까지 살아남는 방식으로 진행합니다.

## 개발 환경

- JDK 17, Java Swing / Java2D, TCP 소켓
- 외부 라이브러리 없이 개발
- 소스 `src/`, 리소스 `res/`, UTF-8 / LF / 스페이스 4칸

## 실행

프로젝트 루트에서 실행합니다. Windows PowerShell 기준입니다.

```powershell
./scripts/build.ps1
./scripts/run-server.ps1
```

다른 터미널에서 클라이언트를 실행합니다. 창을 네 개 열어 동시 접속을 확인할 수 있습니다.

```powershell
./scripts/run-client.ps1 -Nickname player1
./scripts/test.ps1
```

macOS / Linux / Git Bash: `bash scripts/build.sh`로 빌드한 뒤 `java -cp out server.ServerMain`, `java -cp out client.ClientMain localhost 5000 player1`로 실행합니다.

## 폴더 구조

```text
src/common/     공통 상수, 메시지 규약, 모델
src/server/     접속 처리, 로비, 서버 게임 로직
src/client/     클라이언트 통신, Swing 화면, 애니메이션
res/           직접 만든 이미지와 사운드
docs/          개발 규약과 메시지 명세
scripts/       빌드, 실행, 테스트
tests/         공통 메시지 규약 테스트
```

## 현재 상태와 개발 목표

- 기본 제공: TCP 접속, ID 배정, 로그인, 빈 방 목록 응답, 메시지 인코딩·디코딩
- 개발 예정: 방 생성·입장, 준비·시작, 이동·충돌, 물폭탄·연쇄 폭발, 아이템, 승패 판정, 채팅·이모티콘, Swing 화면
- 현재 클라이언트는 접속 확인용 콘솔 프로그램이며 게임 화면은 아직 없습니다.

서버 게임 로직 모듈은 `src/server/game/`에 별도로 구현했습니다. 이동·충돌, 물폭탄·연쇄 폭발, 갇힘·탈락, 승패 판정을 포함하며 **로비·실소켓 연결 전 단계**입니다. 네트워크 담당은 [서버 게임 로직 연결 방법](docs/SERVER_GAME.md)을 참고하세요.

```powershell
./scripts/test.ps1
java -cp out server.game.GameDemo
```

게임 구현 시 판정은 서버가 맡고, 클라이언트는 입력을 보내고 서버 상태를 화면에 표시합니다. 서버는 50ms마다 상태를 갱신하도록 개발합니다.

## 팀과 협업

팀 연결고리: [midogi](https://github.com/midogi), [gksrjsgml03-create](https://github.com/gksrjsgml03-create), [yhkwon28](https://github.com/yhkwon28)

기능별 브랜치에서 작업하고 PR로 합칩니다. 담당자는 팀 회의에서 정합니다.
자세한 내용은 [협업 규칙](CONTRIBUTING.md), [메시지 명세](docs/PROTOCOL.md), [개발 체크리스트](docs/TODO.md), [개발 규약 원본](docs/development-rules.pdf)을 참고하세요.

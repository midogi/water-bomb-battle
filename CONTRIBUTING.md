# 협업 규칙

- JDK 17을 사용하고 프로젝트 루트를 작업 폴더로 실행합니다.
- IDE는 자유롭게 사용하되 개인 설정과 빌드 결과는 커밋하지 않습니다.
- UTF-8, LF, 스페이스 4칸을 사용합니다. 다른 사람의 코드 전체를 자동 포맷하지 않습니다.
- 게임 판정은 서버가 담당합니다. 게임 입력은 방의 큐로 보내고 GameLoop에서 틱마다 처리합니다.
- 클라이언트 수신 스레드에서 Swing 화면을 직접 수정하지 않습니다. `SwingUtilities.invokeLater`로 넘깁니다.

## 작업 순서

```bash
git switch main
git pull --ff-only
git switch -c feature/lobby
```

1. 기능별로 작업하고 빌드와 관련 테스트를 확인합니다.
2. `[server]`, `[client]`, `[common]`, `[docs]` 중 하나로 시작하는 커밋을 작성합니다.
3. 브랜치를 push하고 main을 대상으로 PR을 엽니다.
4. 다른 팀원 한 명이 확인한 뒤 merge합니다.

예: `[server] 물폭탄 연쇄 폭발 구현`

## 공통 규약 변경

`common/`이나 메시지 형식 변경은 세 명이 합의한 뒤 진행합니다. 문서를 먼저 수정하고 변경 이유를 PR에 적습니다.
현재 수치와 메시지 명세는 초안입니다. 미정 항목은 [체크리스트](docs/TODO.md)에 남깁니다.

## 역할 분담

| 역할 | 담당 경로 | 작업 | 담당자 |
|---|---|---|---|
| 게임 로직 | `src/server/game/` | 이동·충돌, 폭탄, 아이템, 승패 판정 | 회의에서 결정 |
| 화면 | `src/client/ui/`, `src/client/game/`, `src/client/resource/` | Swing 화면, 그리기, 애니메이션, 키 입력 | 회의에서 결정 |
| 네트워크·로비 | `src/common/`, `src/server/net/`, `src/server/lobby/`, `src/client/net/` | 메시지, 접속, 방, 채팅 | 회의에서 결정 |

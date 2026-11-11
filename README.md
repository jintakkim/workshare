# 장복인간

고양시 장애인복지관 사회복무요원들이 쓰는 업무 앱입니다.

매일 아침 누가 나눔가게 오전·오후를 맡을지 정하고, 휴가와 식당지원 일정을 한곳에서 관리합니다. 단톡방이나 종이 표로 하던 일을 휴대폰에서 바로 확인하고 입력할 수 있게 만들었습니다.

## 무엇을 하나요

### 직원

- **나눔가게 오늘 배정 확인**: 날짜 카드를 옆으로 넘기며 그날 누가 오전·오후를 맡는지 봅니다. 결과는 08:50에 공개되고, 그 전에는 참여 인원과 남은 시간이 보입니다.
- **오전·오후 선호도**: 오전, 오후, 상관없음 중 하나를 골라두면 다음 추첨부터 반영됩니다.
- **내 일정**: 달력에서 날짜를 눌러 연차·병가·특별휴가·반차·교육훈련을 입력합니다. 그날 08:49 전까지 넣으면 업무 배정에 반영되고, 지난 날짜도 언제든 넣을 수 있어요. 아래에서 지금까지 쓴 휴가 내역을 봅니다.
- **전체 일정**: 날짜별로 누가 휴가·반차·교육인지 한눈에 봅니다.
- **내 월급 조회**: 10일에 받을 예상 금액을 봉급·교통비·식비로 나눠 봅니다. 휴가·반차·교육 기간이 자동으로 반영되고, 하루 교통비는 직접 바꿀 수 있어요. 본인만 볼 수 있습니다.
- **식당지원 일정과 교환**: 내 식당지원 날짜를 확인하고, 다른 직원과 날짜를 맞바꾸자고 요청할 수 있습니다. 상대가 수락하면 바로 바뀝니다.
- **프로필**: 계급·호봉과 소집해제까지 D-day, 실시간 복무 진행률을 보여줍니다. 사진, 소집일·소집해제일, 비밀번호도 여기서 관리합니다.

### 관리자

- 직원 등록·제외와 직군 변경
- 직원 대신 휴가 입력
- 훈련소·직무교육 기간 등록
- 휴일 지정
- 활동(나눔가게, 식당지원)별 담당 직원 관리
- 매달 받은 식당지원표 입력과 하루 필요 인원 설정

## 하루 흐름

| 시각 | 일어나는 일 |
| --- | --- |
| 08:49 전 | 그날 휴가, 식당지원표, 교환 수락 마감 |
| 08:49:10 | 나눔가게 오전·오후 자동 배정 |
| 08:50 | 배정 결과 공개 |
| 09:00 | 만료된 교환 요청 정리 |

배정은 그날 나눔가게에 참여할 수 있는 사람만으로 합니다. 휴가자와 식당지원자는 빠지고, 반차는 근무하는 시간대에만 들어갑니다. 기계실 직원은 시간대마다 한 명이 자리를 지킵니다. 자세한 규칙은 [업무 규칙](docs/business-rules.md)에 있습니다.

## 실행

### 서버 (Docker)

`.env.example`을 `.env`로 복사해 아래 값을 채우고 `docker compose up -d --build`로 띄웁니다.

| 변수 | 용도 |
| --- | --- |
| `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | DB 비밀번호 |
| `APP_ADMIN_PASSWORD` | 관리자 로그인 비밀번호(12자 이상). 아이디는 `admin` |
| `APP_REMEMBER_ME_KEY` | 자동 로그인 서명 키(32자 이상). 바꾸면 모든 자동 로그인이 풀립니다 |
| `SENTRY_DSN` | 선택. Sentry 프로젝트 DSN. 넣으면 오류가 Sentry에 모이고 새 오류마다 메일이 옵니다 |
| `APP_INSTANCE_NAME` | 선택. Sentry에 표시할 서버 이름(기본 `workshare`) |

관리자로 로그인해 직원을 등록하면 직원은 한글 이름으로 로그인합니다.

오류(ERROR 로그)는 Sentry와 별개로 서버의 `app-logs` 볼륨에 하루 한 파일씩 30일 동안 남습니다. 배포해도 지워지지 않습니다.

```bash
docker compose exec app tail -n 200 /app/logs/error.log
```

HTTPS 뒤에서 운영하면 `.env`에 `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`와 `SERVER_FORWARD_HEADERS_STRATEGY=framework`를 추가합니다.

### 배포 (GitHub Actions)

**Actions → WorkShare CI and deploy → Run workflow → `main`** 을 실행하면 테스트를 통과한 `main`을 서버에 배포합니다.

처음 한 번은 서버에 self-hosted runner(라벨 `workshare-production`)를 등록하고, 저장소 변수 `WORKSHARE_DEPLOY_DIR`에 서버의 `workshare` 폴더 절대 경로를 넣어야 합니다.

### 개발

DB만 `docker compose up -d mysql`로 띄우고 `./mvnw spring-boot:run`으로 실행합니다. 테스트는 H2를 씁니다.

## 설정

나눔가게 오전·오후 인원은 기본으로 절반씩(홀수면 오전이 한 명 더) 나눕니다. 인원수별로 다르게 하고 싶으면 `application.yml`에 적습니다.

```yaml
app:
  sharing-shop-quotas:
    5:
      morning: 2
      evening: 3
```

마감·배정 시각(`app.leave-cutoff`, `app.cafeteria-support-cutoff`, `app.allocation-time`)도 같은 파일에 있습니다.

## 문서

- [업무 규칙](docs/business-rules.md): 권한, 마감, 배정, 식당지원과 교환 규칙
- [기능 테스트](docs/business-tests.md): 규칙별 테스트 시나리오
- [변경 기록](docs/changelog.md): 운영 배포 뒤 바뀐 기능·개선·버그 수정
- [동시 저장과 잠금](docs/concurrency.md), [엔티티와 DB](docs/persistence.md)


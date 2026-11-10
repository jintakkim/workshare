# WorkShare — 일나누기

Spring Boot 3.5.16 · Java 21 · Thymeleaf · Spring Security · Spring Data JPA · Flyway · MySQL로 구성한 애플리케이션입니다. 웹 서버는 Spring 단일 서버이고 MySQL은 Docker 컨테이너 한 개로 실행합니다. H2는 테스트 의존성으로만 사용하며 실행 JAR에는 포함하지 않습니다.

## 문서

- [업무 규칙](docs/business-rules.md): 현재 확정된 권한·마감·휴가·배분·지원표·교환 규칙을 정리한 기준 문서입니다.
- [업무 규칙 기능 테스트](docs/business-tests.md): 규칙별 테스트와 실제 로그인·신청·배정·조회 흐름, 검증 범위.
- [동시 저장과 잠금 기준](docs/concurrency.md): 전역 락 없이 원자적 저장과 대상별 JPA 행 잠금을 적용하는 범위.
- [문서 목록](docs/README.md)

## 실행

프로젝트·서비스 이름은 **WorkShare**, 폴더·빌드 이름은 `workshare`, Java 기본 패키지는 `com.jangbokhuman.workshare`입니다.

### 서버 PC에서 Docker로 실행

Docker와 Compose가 설치된 서버에서 저장소를 복제하고 프로젝트 루트에서 실행합니다. 서버에 Java나 Maven을 설치할 필요는 없습니다.

```bash
cp .env.example .env
# .env에서 MYSQL_PASSWORD, MYSQL_ROOT_PASSWORD, APP_ADMIN_PASSWORD, APP_REMEMBER_ME_KEY를 각각 지정합니다.
docker compose up -d --build
docker compose logs -f app
```

세 비밀번호는 서로 다른 값으로 설정합니다. 예를 들어 `openssl rand -hex 24`를 값마다 별도로 실행합니다. Compose와 Spring Boot에서 함께 읽을 수 있도록 `.env`에 따옴표 없이 영문과 숫자로 입력할 수 있습니다.

서버 브라우저에서 [로그인](http://127.0.0.1:8080/login)을 열어 확인합니다. 외부 직원용으로 Cloudflare Tunnel을 같은 서버에서 실행한다면 터널 대상을 `http://127.0.0.1:8080`으로 연결합니다. HTTPS로 접속하는 운영 환경의 `.env`에 `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`와 `SERVER_FORWARD_HEADERS_STRATEGY=framework`를 설정한 뒤 `docker compose up -d`로 반영합니다.

코드를 업데이트할 때는 `git pull`과 `docker compose up -d --build`를 실행합니다. 직원 계정과 일정은 MySQL 볼륨에 남습니다. 월별 식당지원표는 앱의 **관리자 → 지원표 → 식당지원**에서 해당 월을 골라 저장합니다.

### GitHub Actions로 운영 배포

브랜치에 푸시하거나 `main`을 대상으로 PR을 열고 수정하면 GitHub에서 Java 21로 테스트합니다. 운영에 반영할 때는 **Actions → WorkShare CI and deploy → Run workflow → `main`**을 선택합니다. 실행 시점의 `main` 커밋이 테스트를 통과하면 그 커밋을 서버 노트북에 배포합니다.

운영 서버 연결은 한 번만 설정합니다.

1. 운영 노트북에서 기존 `workshare` 폴더에 들어가 `pwd`로 절대 경로를 확인합니다. `main` 브랜치, 기존 `.env`, 실행 중인 MySQL이 있어야 합니다.
2. 비공개 GitHub 저장소의 **Settings → Actions → Runners → New self-hosted runner**에서 Linux를 고릅니다. 화면에 나온 설치 명령을 운영 노트북에서 실행하고 `./config.sh` 명령에 `--labels workshare-production`을 추가합니다. 설치 후 `sudo ./svc.sh install`과 `sudo ./svc.sh start`로 러너를 서비스로 실행합니다.
3. 러너 서비스 계정으로 기존 체크아웃에서 `git fetch origin main`과 `docker compose ps`가 성공하는지 확인합니다. GitHub 접근이 필요하면 해당 체크아웃에 읽기 전용 배포 키를 설정합니다.
4. GitHub **Settings → Secrets and variables → Actions → Variables → New repository variable**에서 `WORKSHARE_DEPLOY_DIR`에 1번의 절대 경로를 입력합니다.

배포는 기존 `.env`와 MySQL 볼륨을 사용하며 앱 컨테이너만 빌드하고 교체합니다. 마지막에 `/actuator/health`로 앱과 DB 연결 상태를 확인합니다. 서버 체크아웃에 수정한 파일이 있거나 이미 더 최신 커밋으로 배포했다면 작업을 중단합니다. Cloudflare Tunnel의 연결 설정은 그대로 사용합니다.

### 기존 직원과 지원표를 서버로 옮기기

기존 PC에서 MySQL을 덤프합니다. 아래 파일에는 직원과 일정 데이터가 들어가므로 서버로 별도 전송합니다.

```bash
umask 077
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -u root --single-transaction --set-gtid-purged=OFF --no-tablespaces "$MYSQL_DATABASE"' > workshare-backup.sql
```

서버에서 앱을 처음 띄우기 전에 MySQL만 실행하고 새 DB에 덤프를 복원합니다. 복원할 스키마는 앱 버전과 호환되어야 합니다.

```bash
docker compose up -d --wait mysql
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -u root "$MYSQL_DATABASE"' < workshare-backup.sql
docker compose up -d --build
```

### IDE 또는 Maven으로 개발 실행

JDK 21을 사용합니다. Maven은 Wrapper가 내려받습니다. Docker 실행과 같은 `.env`를 사용합니다.

```bash
export JAVA_HOME=/path/to/jdk-21
docker compose up -d --wait mysql
./mvnw spring-boot:run
```

macOS에서 설치된 JDK 21을 선택하려면 `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`을 사용할 수 있습니다.

- 로그인: <http://localhost:8080/login>
- 직원 달력: <http://localhost:8080/>
- 관리자 페이지: <http://localhost:8080/admin>
- 관리자 기본 아이디: `admin`. `APP_ADMIN_USERNAME`으로 변경할 수 있습니다.
- 관리자 비밀번호는 `APP_ADMIN_PASSWORD`로 지정합니다. 12자 이상, UTF-8 72바이트 이하입니다.
- 자동 로그인 쿠키 서명 키는 `APP_REMEMBER_ME_KEY`로 지정합니다. 32자 이상 임의 문자열(예: `openssl rand -hex 32`)이며 관리자 비밀번호와 달라야 합니다. 이 키를 바꾸면 모든 자동 로그인이 해제됩니다.
- 같은 브라우저에서는 로그인 후 90일 동안 자동 로그인이 유지됩니다. 공용 기기에서는 사용 후 로그아웃합니다.

관리자로 로그인해 `/admin/employees`에서 직원의 **한글 이름**, 비밀번호와 **직군**을 먼저 등록합니다. 별도의 영문 로그인 아이디 없이 등록된 이름(예: 홍길동)과 비밀번호로 로그인합니다. 이름은 공백 없는 한글 1~30자이며 동명이인은 중복 등록할 수 없습니다. 새 직원의 비밀번호는 8~20자이며 영문 대소문자, 숫자, 지정 특수문자 `!@#$%_-`만 허용합니다. 문자 종류별 필수 조합은 강제하지 않습니다. 허용 문자가 모두 ASCII라 최대 20바이트로 제한되므로 별도의 바이트 길이 검사 없이 BCrypt 해시로 저장합니다. 기존 비밀번호는 변경하지 않으며 로그인 시에는 새 등록 규칙을 적용하지 않습니다. 예시 직원이나 공용 기본 직원 비밀번호를 자동 생성하지 않습니다.

패키징 및 실행:

```bash
./mvnw test
./mvnw package
java -jar target/workshare-0.0.1-SNAPSHOT.jar
```

환경변수 `PORT`로 웹 포트를 바꿀 수 있습니다. 다른 앱이 8080을 사용 중이면 `PORT=8081 ./mvnw spring-boot:run`으로 실행하고 <http://localhost:8081>로 접속합니다. HTTPS 운영 환경에서는 `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`로 설정합니다.

### MySQL 연결 정보

운영 설정은 `src/main/resources/application.yml` 하나에 둡니다. IDE와 JAR 모두 별도 프로필 없이 아래 Docker MySQL에 연결합니다.

| 항목 | 로컬 기본값 |
| --- | --- |
| 이미지 | `mysql:8.4.8` |
| 호스트·포트 | `127.0.0.1:3307` |
| 데이터베이스 | `workshare` |
| 앱 DB 계정 | `ted` |
| 앱 DB 비밀번호 | `MYSQL_PASSWORD`로 지정 |
| 관리자 로그인 계정 | `admin` |
| 관리자 로그인 비밀번호 | `APP_ADMIN_PASSWORD`로 지정 |

관리자 로그인 비밀번호와 DB 비밀번호는 서로 별개입니다. MySQL은 로컬 주소에만 포트를 열고, 데이터는 Docker의 `workshare_mysql-data` 볼륨에 보관합니다. `docker compose stop mysql`로 중지하고 `docker compose up -d --wait mysql`로 다시 실행할 수 있습니다. 볼륨은 유지됩니다.

```bash
docker compose ps mysql
docker compose logs --tail=50 mysql
# 비밀번호는 프롬프트에서 입력합니다.
docker compose exec mysql mysql -uted -p workshare
```

기본값을 바꿀 때는 `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`를 `.env`나 환경변수에 지정합니다. IDE에서 실행할 때도 프로젝트 루트를 작업 디렉터리로 사용합니다. 이미 초기화된 MySQL 볼륨의 비밀번호를 바꿀 때는 DB 계정 비밀번호를 변경한 뒤 설정을 맞춥니다.

MySQL은 `db/mysql`의 전용 Flyway 초기 스키마를 사용합니다. H2용 `db/migration`은 테스트 리소스에서만 사용합니다. 새 MySQL DB에 기존 H2 데이터가 자동 복사되지는 않습니다. 날짜·배정 시각은 앱에서 한국 시간으로 처리하고 DB의 Instant 값은 UTC로 저장합니다.

### H2는 테스트 전용

H2 의존성은 Maven `test` scope이고, H2 초기 스키마와 `application-test.yml`은 `src/test/resources`에만 둡니다. 실행 JAR에는 MySQL 드라이버와 MySQL 초기 스키마만 포함합니다. 기본 테스트는 인메모리 H2의 기본 모드에서 JPA 저장 동작을 검증합니다.

## 최종 배정 기준

모든 시각은 `Asia/Seoul` 기준입니다.

1. 직원은 달력에서 미래 날짜의 휴가를 미리 저장·수정할 수 있습니다. 나눔가게 시간대 선호도는 직원별로 하나를 저장하며 날짜가 바뀌어도 유지합니다.
2. 해당 날짜 **08:49:00부터** 휴가 입력을 잠급니다. 즉 08:48:59.999…까지 가능합니다.
3. **08:49:10**에 하루 한 번 자동 배정하고 **08:50**에 결과를 공개합니다. 토·일요일과 관리자가 추가한 휴일은 제외합니다.
4. 활동에 가입한 직원 중 미입력자는 정상 출근·선호 없음입니다. 휴가는 배정 대상에서 제외합니다. 식당 근무자는 나눔가계 활동에 참여할 수 없으며, 참여 규칙이 없는 직군도 제외합니다.
5. 오전 반차는 오후만, 오후 반차는 오전만 배정합니다. 오전은 09:00~14:00, 오후는 14:00~18:00입니다. 자리가 부족하면 미배정될 수 있습니다.
6. **상주 담당을 따로 제외하지 않습니다.** 전체 출근 인원을 나누고, 각 시간에 업무하지 않는 직원이 자리를 지킵니다.
7. 오전·오후 기계실 상주 후보를 정하고, 각 조합에서 남은 직원의 나눔가게 배정을 계산합니다. 나눔가게 운영, 기계실 상주, 겸임 최소화, 참여 인원 순으로 비교합니다. 기계실 상주는 필수이며 등록 인원이 0명이면 두 시간대에 기계실 부족을 경고합니다.
8. 기본 목표는 오전 `ceil(가능 인원 / 2)`, 오후는 나머지입니다. 운영 조건이 같은 조합은 목표 비율과 직원 선호로 비교합니다. 조건이 같은 직원끼리는 `SecureRandom`으로 추첨합니다.

정상 출근자만 있을 때:

| 출근 인원 | 오전 | 오후 |
| --- | --- | --- |
| 5명 | 3명 | 2명 |
| 4명 | 2명 | 2명 |
| 3명 | 2명 | 1명 |
| 2명 | 1명 | 1명 |
| 1명 | 같은 직원 1명 | 같은 직원 1명 |
| 0명 | 배정 불가 | 배정 불가 |

출근자가 모두 오전만 근무할 수 있다면 전원을 오전에 배정하고 오후 인원 부족을 경고합니다. 정상 출근자가 한 명뿐이면 두 시간대를 맡습니다.

예를 들어 출근자 5명 중 오후 선호가 2명이면 그 2명을 오후에 배정할 수 있습니다. 오후 선호가 3명이면 가능한 최적 배정 중 추첨해 2명을 오후에 배정합니다. 오후 반차자의 오후 선호와 오전 반차자의 오전 선호는 반차 제약 때문에 충족하지 못합니다.

## 달력 및 관리자 화면

직원 화면은 나눔가게 날짜 카드, 본인 휴가 달력, 본인 지원 일정·교환 요청함, 직원 프로필로 나뉩니다. 참여 현황과 확정 배정은 날짜 카드에서 확인합니다. 달력의 휴가·반차 뱃지는 로그인한 직원 본인의 신청입니다. 나눔가게 화면에서 본인의 시간대 선호도를 저장하며, 변경하면 다음 추첨부터 반영합니다. 휴가 취소는 해당 날짜를 선택해 `정상 출근`으로 저장합니다. 휴가 사유는 수집하지 않습니다.

관리자 페이지는 `/admin`으로 분리되어 있습니다. 직원 계정 등록·인원 제외, 추가 휴일 등록·취소를 담당합니다. 직원은 관리자 URL과 관리자 POST 요청에 접근할 수 없습니다. 관리자는 휴가 일정에서 직원을 선택해 연차·병가·반차를 대신 입력할 수 있으며 직원과 같은 마감 규칙을 적용합니다.

- 직원 등록 수와 활동 참여 인원에는 상한을 두지 않습니다. 활동 참여 자격과 당일 휴가·반차를 기준으로 배정합니다. 직원이 추가되거나 제외되어도 과거 배정 기록은 유지합니다.
- 신규 직원의 기본 소집일은 계정 등록일입니다. 내 프로필에서 소집일을 입력하면 21개월 기준 소집해제일이 바로 채워지고, 실제 날짜가 다르면 직접 고쳐 저장할 수 있습니다. 소집해제일은 소집일보다 늦고 오늘보다 이르지 않아야 합니다.
- 관리자가 인원 제외를 마감 이후에 하면 다음 날부터 적용합니다. 근무 기간이나 인원 제외를 변경해도 확정된 배정은 유지합니다.
- 공휴일은 관리자 화면에서 직접 등록합니다. 정부 공휴일 API를 자동 조회하지 않습니다.
- 휴일 변경도 당일 08:49 이전까지만 가능합니다. 당일 마감 후와 과거 날짜는 변경할 수 없습니다.
- 신청이 있는 날짜를 휴일로 바꿔도 신청은 보관합니다. 휴일을 취소하면 기존 신청이 다시 적용됩니다. 토·일요일은 추가 휴일을 취소해도 휴무입니다.

## 객체와 책임

아래 트리는 `src/main/java`의 운영 코드 타입을 패키지별로 보여줍니다.

```text
com.jangbokhuman.workshare/
├─ WorkShareApplication                                Spring Boot 실행 진입점. 스케줄링과 WorkSettings 바인딩 활성화
├─ common/                                            여러 영역이 함께 쓰는 기본 타입
│  ├─ BusinessException                                사용자에게 메시지를 보여줄 업무 규칙 위반 예외
│  └─ ErrorCode                                        입력 오류·없음·중복·마감·불허·충돌 분류
├─ employee/                                           직원 계정·직군·프로필·담당 업무
│  ├─ Employee                                         직원 계정, 현재 직군, 근무 시작일과 제외일
│  ├─ EmployeeRepository                               로그인 이름 조회와 직군·가입 변경에 필요한 직원 행 잠금
│  ├─ WorkGroupType                                    식당·운영지원·기계실·3층 직군 코드와 표시 이름
│  ├─ LoginNames                                       가입 및 로그인에 쓰는 직원 이름 정규화
│  ├─ RosterService                                    계정 생성, 직군 변경, 인원 제외, 직원 화면 정보 조회
│  ├─ EmployeePhoto                                    직원 ID로 연결되는 프로필 이미지 엔티티
│  ├─ EmployeePhotoRepository                          직원 사진과 사진이 등록된 직원 ID 조회
│  ├─ EmployeeProfileService                           사진 저장과 복무 기간 변경
│  ├─ ProfilePhotoConverter                            업로드 사진을 형식·해상도 확인 후 최대 512px JPEG로 변환
│  ├─ EmployeeActivityReader                           직원 화면의 가입 활동 이름 조회 계약. 활동 영역이 구현
│  ├─ EmployeeUserDetailsService                       로그인 이름으로 관리자·직원 계정 조회
│  ├─ EmployeeRegistered                               직원 등록 이벤트. 활동별 자동 가입 리스너가 처리
│  ├─ EmployeeWorkGroupChanged                         직군 변경 이벤트. 활동 영역이 자격을 잃은 가입을 정리
│  ├─ EmployeeServicePeriodChanged                     복무 기간 변경·인원 제외 이벤트. 식당지원 일정 정리 리스너가 처리
│  └─ AdminEmployeeController                          관리자 직원 등록·인원 제외 요청
├─ activity/                                           활동 종류·가입·참여 자격·공통 업무 배정
│  ├─ Activity                                         코드에서 정의한 활동 종류, 표시 이름, 설명
│  ├─ ActivityRegistration                             활동과 직원의 가입 관계 한 건
│  ├─ ActivityRegistrationRepository                   가입 여부·가입자 명단·활동별 인원 집계 조회
│  ├─ ActivityRegistrationService                      직원별 가입·탈퇴와 직군 변경 이벤트를 받아 자격을 잃은 가입 정리
│  ├─ RegisteredActivityReader                         직원별 가입 활동 이름 조회(EmployeeActivityReader 구현)
│  ├─ ActivityExclusionReader                          날짜별 활동 후보 제외 명단 조회 계약. 식당지원 패키지가 구현
│  ├─ WorkTaskAllocationStatus                         날짜별 배정 확정 여부 조회(DailyAllocationStatus 구현)
│  ├─ ActivityRegistrationDeleted                      탈퇴한 활동과 직원 ID를 전달하는 삭제 이벤트
│  ├─ ActivityRegistrationPolicy                       현재 직군과 인원 제외 상태로 가입 가능 여부 판단
│  ├─ ActivityManagementService                        관리자 활동 카드와 가입·탈퇴 대상 직원 목록 구성
│  ├─ ActivityAlbumController                          관리자 활동 상세 조회와 직원별 가입·탈퇴 요청
│  ├─ DailyActivityParticipationPolicy                 날짜별 근무 기간·가입 여부와 지원으로 인한 참여 제외 확인
│  ├─ ParticipationDecision                            참여 허용 여부와 불허 사유를 담는 결과
│  ├─ ParticipationDeniedException                     불허 결정에 따라 가입·참여 요청을 거절하는 예외
│  ├─ ActivityWorkGroupRestriction                     활동별 가입 불가 직군을 정의하는 규칙 계약
│  ├─ AllocationScheduler                           app.allocation-time·앱 시작 시 자동 배정 호출
│  ├─ DailyAllocationService                        오늘의 자동 배정을 활동별 트랜잭션으로 실행하고 실패한 활동은 로그로 남김
│  ├─ DailyActivityAllocator                        활동 전용 일일 업무 생성·배정의 공통 실행 계약
│  ├─ RosterReader                                  가입자·근무 기간·휴가·지원 일정을 합쳐 당일 배정 후보 구성
│  ├─ Worker                                        배정 후보의 직원 ID·이름·근무 상태를 담는 값
│  ├─ Duty                                          저장할 업무의 코드와 표시 이름을 담는 값
│  ├─ TaskRequirement                               한 업무의 종류·근무 시간대·필요 인원을 담는 배정 조건
│  ├─ TaskAllocation                                업무 하나의 담당 직원과 부족 경고를 담는 배정 결과
│  ├─ AssignmentGuard                               배정 저장 전 가입·지원 제외·출근 상태·시간대 조건 재검사
│  ├─ WorkTask                                      날짜별 확정 업무의 인원, 확정 시각, 배정과 경고를 보관하는 엔티티
│  ├─ TaskAssignment                                확정 당시 담당 직원의 ID·이름·근무 상태를 보관하는 엔티티
│  ├─ TaskAssignmentRepository                      업무 ID별 배정 기록 조회와 저장
│  ├─ WorkTaskRepository                            활동·날짜별 업무 조회와 배정 여부 확인
│  ├─ WorkTaskService                               활동 하나의 전용 배정기(DailyActivityAllocator) 실행
│  ├─ cafeteria/                                       식당지원 활동. 관리자가 입력한 월별 명단·필요 인원·맞교환
│  │  ├─ CafeteriaSupportAssignment                    날짜별 식당지원 담당 직원 한 명을 저장하는 엔티티
│  │  ├─ CafeteriaSupportRequirement                   적용일별 하루 필요 인원을 저장하는 엔티티
│  │  ├─ CafeteriaSupportExchangeRequest               두 직원의 지원일 맞교환 요청과 만료 시각
│  │  ├─ CafeteriaSupportScheduleService               월별 명단 저장과 필요 인원 변경
│  │  ├─ CafeteriaSupportQueryService                  관리자·직원용 월별 식당지원표 조회
│  │  ├─ CafeteriaSupportAvailabilityChecker           명단 등록·맞교환 시 직원의 식당지원 가능 여부 판정
│  │  ├─ CafeteriaSupportExchangeService               지원일 맞교환의 요청·수락·취소·거절과 교환 화면 조회
│  │  ├─ CafeteriaSupportDeadline                      식당지원 변경 마감과 필요 인원 적용일 계산
│  │  ├─ CafeteriaSupportExclusionReader               당일 식당지원 담당자를 다른 활동 후보 제외 명단으로 제공하고 인원·휴가 겹침 경고
│  │  ├─ CafeteriaSupportRosterCleaner                 탈퇴·복무 기간 변경·인원 제외 시 변경 가능한 지원 일정과 교환 요청 정리
│  │  ├─ CafeteriaSupportAutoRegistration              직원 등록 시 식당 외 직군을 식당지원에 가입
│  │  ├─ RestaurantCafeteriaSupportRestriction         식당 직군의 식당지원 가입 제한
│  │  ├─ CafeteriaSupportExchangeCleanupScheduler      매일 09:00에 만료된 교환 요청 정리
│  │  ├─ CafeteriaSupportAdminController               관리자 월별 식당지원표·필요 인원 요청
│  │  ├─ CafeteriaSupportController                    직원 지원 일정과 맞교환 요청
│  │  └─ CafeteriaSupportScheduleController            직원·관리자 공용 월별 식당지원표 조회
│  └─ sharingshop/                                     나눔가게 화면·업무 배정
│     ├─ SharingShopController                         직원·관리자용 날짜 카드와 본인 시간대 선호도 저장 요청
│     ├─ SharingShopApiController                      날짜별 나눔가게 카드 데이터를 제공하는 REST API
│     ├─ SharingShopQueryService                       휴무·참여자·예정 인원·확정 배정과 결과 공개 시각을 반영한 카드 조회
│     ├─ SharingShopTaskReader                         날짜별 나눔가게 확정 업무와 담당 직원 조회
│     ├─ SharingShopDailyAllocator                     당일 나눔가게 오전·오후 업무를 생성하고 배정을 확정
│     ├─ PreferenceShiftAllocationPolicy               전원 배정, 기계실 상주, 목표 비율과 시간대 선호를 반영한 오전·오후 배정
│     ├─ ShiftAllocation                               직원별 담당 시간대와 배정 경고를 담는 결과
│     ├─ Preference                                    오전·오후·상관없음 중 직원이 고른 시간대 선호
│     ├─ SharingShopWorker                             직원별 근무 상태·시간대 선호·기계실 직군 여부
│     ├─ SharingShopStaffing                           추첨 후보와 시간대별 기계실 출근 인원 조회
│     ├─ FacilityCoverage                              시간대별 기계실 상주 부족 판정
│     ├─ SharingShopTimePreference                     직원별 시간대 선호도 한 건을 저장하는 엔티티
│     ├─ SharingShopTimePreferenceRepository           직원 ID로 시간대 선호도 조회·저장
│     ├─ SharingShopTimePreferenceService              시간대 선호도 조회·변경과 가입 삭제 이벤트 처리
│     ├─ SharingShopTimePreferenceChangePolicy         가입 여부와 현재 제외 상태로 선호도 변경 권한 판단
│     ├─ ShiftQuota                                    오전·오후 필요 인원과 기본 인원 배분 규칙
│     ├─ ShiftQuotaService                             가능 인원수에 맞는 YAML 배분 기준 또는 기본값 선택
│     ├─ SharingShopSettings                           app.sharing-shop-quotas 인원수별 배분 예외 바인딩
│     ├─ SharingShopAutoRegistration                   직원 등록 시 운영지원·기계실 직군을 나눔가게에 가입
│     ├─ RestaurantSharingRestriction                  식당 직군의 나눔가게 참여를 제한
│     └─ SharingShopConfiguration                      나눔가게 배정 정책과 식당 직군 제한을 Spring 빈으로 등록
├─ attendance/                                         날짜별 근무 상태(정상 출근·반차·연차·병가)
│  ├─ Attendance                                       정상 출근·연차·병가·오전 및 오후 반차와 시간대별 근무 가능 여부
│  ├─ LeaveRequest                                     직원·날짜별 근무 상태와 수정 시각을 저장하는 엔티티
│  ├─ LeaveRequestRepository                           직원별·날짜별 휴가 신청 조회·저장
│  ├─ LeaveRequestService                              본인·관리자 대리 신청의 공통 저장 및 수정
│  ├─ LeaveRequestValidator                            휴가 신청일의 마감·근무일·직원 근무 기간 검증
│  ├─ LeaveCalendarService                             본인·관리자 대리 휴가 달력의 날짜·휴일·근무 상태와 입력 가능 여부 구성
│  ├─ TeamLeaveCalendarService                         전체 직원 휴가 일정 달력 구성
│  └─ AttendanceController                             팀 휴가 달력과 본인 근무 상태 신청 요청
├─ calendar/                                           근무일·휴일·마감
│  ├─ BusinessCalendar                                 주말과 등록 휴일을 반영해 근무일인지 판단
│  ├─ Holiday                                          별도 휴일 날짜와 사유를 저장하는 엔티티
│  ├─ HolidayRepository                                날짜 또는 기간으로 등록 휴일 조회·저장
│  ├─ HolidayService                                   별도 휴일 추가·해제와 변경 마감 적용
│  ├─ HolidayCalendarService                           관리자 월별 휴일 달력에 표시할 날짜와 휴일 정보 구성
│  ├─ HolidayAdminController                           관리자 휴일 추가·해제 요청
│  ├─ DailyAllocationStatus                            날짜별 배정 확정 여부 조회 계약. 활동 영역이 구현
│  ├─ WorkPeriod                                       오전·오후 근무 시간과 시간대별 배정 가능 여부
│  ├─ WorkTime                                         설정 시간대의 현재 시각, 근무 변경 적용일, 배정 시각 판단
│  └─ WorkScheduleDeadlineValidator                    휴가·휴일 변경의 08:49 마감 검증
├─ web/                                                여러 영역을 연결하는 웹 진입점
│  ├─ AdminController                                  관리자 메뉴 화면과 관리자 대리 휴가 신청
│  ├─ AdminSection                                     관리자 메뉴 구분
│  ├─ LoginController                                  로그인 화면
│  ├─ LeaveDashboardController                         본인 휴가 달력 화면
│  ├─ EmployeeProfileController                        직원 목록, 직원 프로필, 사진 조회·저장
│  ├─ EmployeePasswordController                       본인 비밀번호 변경
│  ├─ EmployeeServicePeriodController                  본인 복무 기간 변경
│  ├─ EmployeeManagementController                     관리자 직원 상세, 사진, 직군 변경
│  ├─ EmployeeProfileModel                             직원·관리자 프로필 화면 모델 구성
│  └─ WebExceptionHandler                              컨트롤러가 처리하지 않은 업무 예외·사진 용량 초과·참여 불가 응답
└─ config/                                             전역 설정·인증
   ├─ AuthenticatedUser                                로그인 이름과 권한에 직원 ID를 포함하는 인증 사용자
   ├─ DomainConfiguration                              Clock을 Spring 빈으로 등록
   ├─ SecurityConfiguration                            비밀번호 처리·관리자 접근 권한·로그인 이동·자동 로그인 설정
   └─ WorkSettings                                     시간대·마감·관리자 계정·자동 로그인 키를 바인딩
```

활동 종류는 `activity.Activity` enum으로 정의하며 활동 마스터 테이블은 두지 않습니다. 새 활동의 이름·설명은 enum에 추가하고, 자동 업무가 필요하면 `DailyActivityAllocator` 구현을 추가합니다. 공통 `shiftAllocation` 플래그는 없습니다. 나눔가게의 오전·오후 생성은 `activity.sharingshop.SharingShopDailyAllocator`에서만 처리하며 다른 활동에는 자동 적용하지 않습니다.

배정 정책은 Spring, JPA, 웹 세션에 의존하지 않습니다. 나눔가게 오전·오후 분배는 `PreferenceShiftAllocationPolicy`에만 있어서, 분배 규칙을 바꿀 때 컨트롤러나 배정표 저장 형식을 바꾸지 않습니다. 날짜·마감 관련 코드는 주입한 `Clock`을 사용합니다.

기계실 상주자의 해당 시간대를 후보에서 빼고, 한 시간대만 가능한 직원부터 배정합니다. 종일 근무자는 목표 비율과 선호에 맞춰 나눕니다. 같은 출근 조건과 선호도를 가진 기계실 직원은 대표 후보를 사용하므로 직원별 배정 조합을 전부 만들지 않습니다.

## 직군과 활동 참여 제한

`/admin/employees`에서 직원 등록 시 직군을 선택합니다. 직군은 식당(`RESTAURANT`), 운영지원(`GENERAL`), 운영지원(기계실)(`FACILITY_SUPPORT`), 3층(`THIRD_FLOOR`), 발달지원(`DEVELOPMENT_SUPPORT`) 중 하나만 선택합니다. 기본 활동은 나눔가계 활동(`SHARING_SHOP`)입니다.

- `ADMIN / STAFF`는 Spring Security의 관리 기능 접근 권한입니다. 직군과 활동 참여 자격은 별도 정책으로 검사합니다.
- 식당 근무자는 나눔가계 활동의 선호도 신청과 자동 배정에 참여할 수 없습니다. 관리자가 담당 직원으로 지정하려는 요청도 서버에서 403으로 거절합니다.
- 활동별 가입 제한은 `ActivityWorkGroupRestriction` 구현으로 정의합니다. DB에 직군별 허용 규칙을 저장하지 않습니다.
- 직군이 미지정이거나 활동에 가입하지 않은 직원은 참여할 수 없습니다.
- 식당 근무자도 로그인과 공통 휴가 신청을 할 수 있습니다. 로그인한 직원은 활동과 배정표를 조회할 수 있으며 참여 불가 사유를 표시합니다. 조회 권한과 참여 자격을 구분합니다.
- 휴가·반차는 **직원 + 날짜**로 저장해 모든 활동에 적용합니다. 나눔가게 시간대 선호도는 **직원별 하나**로 저장합니다. 저장 인자는 직원 ID와 시간대 선호도이며 날짜·시각·활동 코드를 받지 않습니다. 다른 활동에는 이 선호도를 적용하지 않습니다.
- 직군은 직원별 현재 값 하나만 저장하며 변경 즉시 덮어씁니다. 확정된 업무 배정은 유지합니다. 활동 가입·탈퇴도 즉시 반영합니다. 직원 계정 등록과 활동 가입은 별도이며, 가입 적용일은 두지 않습니다.
- 직원 등록·직군 변경·활동 가입은 인원수로 제한하지 않습니다.

배정 전 활동 자격으로 후보를 걸러낸 뒤 인원 비율과 선호도를 계산합니다. 저장 직전 `AssignmentGuard`가 직원의 실제 직군·출근 상태·활동 자격을 다시 검사하므로, 잘못된 배정 전략이나 수동 요청으로 금지된 직원을 추가할 수 없습니다.

활동별 담당 직원은 활동 앨범의 상세 화면에서 선택합니다. 직군별 허용·차단을 변경하는 관리자 화면과 API는 제공하지 않습니다. 새로운 활동별 직군 제한이 생기면 `ActivityWorkGroupRestriction` 구현을 Spring Bean으로 추가합니다. 기존 웹·배정 코드에서 직군별 조건문을 반복하지 않습니다.

## 활동의 업무 배정

나눔가게는 매일 08:49:10에 오전·오후 업무를 함께 추첨합니다. 관리자가 별도 업무를 추가하는 기능은 없습니다.

새 활동을 추가하려면 `Activity` enum에 등록하고 활동별 패키지에서 업무 규칙을 정의합니다. 자동 배정이 필요한 활동은 해당 패키지에 `DailyActivityAllocator` 구현을 추가합니다.

### 업무 중심 데이터 구조

```text
WorkTask (업무 하나, Long IDENTITY)
 ├─ activityCode(enum) / workDate / code / name / period / requiredWorkers
 ├─ createdAt / assignedAt (배정을 확정하며 함께 저장)
 └─ warnings: 해당 업무의 경고

TaskAssignment (배정 기록 하나, Long IDENTITY)
 ├─ task: WorkTask 참조
 └─ 배정 당시 직원 ID·이름·출근 상태
```

활동과 날짜는 업무의 조회 조건입니다. 나눔가게 오전·오후는 `PreferenceShiftAllocationPolicy`로 함께 계산하고 업무 2개로 저장합니다.

각 `WorkTask`는 확정 시각과 경고를 관리합니다. 담당자 기록은 `TaskAssignment`에서 업무를 참조하며, 화면에 필요한 목록은 업무 ID로 조회합니다. 스키마를 바꿀 때는 MySQL과 H2에 같은 번호의 새 Flyway 버전을 추가합니다.

## 중복 실행과 복구

직원 등록은 DB 이름 고유 제약으로 중복을 막습니다. 휴가·선호도는 JPA 조회·변경·`save()`를 사용합니다. 활동 마스터와 앱 전체 잠금 테이블은 없습니다. 활동 가입·직군 변경·선호도 저장은 해당 직원 행을, 지원표·교환은 해당 지원 일정과 요청 행을 잠급니다. 자동 배정 요청이 겹치면 업무 고유 제약으로 중복 저장을 막고 충돌한 트랜잭션을 롤백합니다. [동시 저장과 잠금 기준](docs/concurrency.md)에 대상과 이유를 정리했습니다.

자동 배정은 매일 `app.allocation-time`(기본 08:49:10)과 서버 시작 시에 확인합니다. 그 시각 전에 서버가 시작하면 배정 시각까지 기다립니다. 그 시각부터 서버가 시작하면 오늘 아직 없는 업무를 생성하고 바로 배정을 확정합니다. 확정된 결과는 유지합니다.

DB와 스케줄러를 포함한 단일 프로세스 운영을 전제로 합니다. 자동 배정은 `app.allocation-time`에 맞춰 매일 실행합니다. 입력 마감은 배정 시각보다 빨라야 하며, `app.allocation-result-time`은 배정 시각 이후로 설정합니다.

## 검증

`./mvnw test`는 기능·통합 96회와 배분 정책·참여 제한·출근 시간대 27회, 패키지 순환 의존 검사 3회를 더해 총 126회를 검증합니다. 로컬에서 `heif-thumbnailer`가 없으면 HEIC 업로드 테스트 한 개를 건너뜁니다. [업무 규칙별 테스트 목록](docs/business-tests.md)에서 시나리오를 확인할 수 있습니다.

주요 확인 사항은 다음과 같습니다.

- 출근 상태 5종 × 선호 3종 × 5명 = **759,375개 조합**의 인원 비율·반차·휴가 제약·최대 선호 충족.
- 0명부터 1,100명까지의 배정, 1명 겸임, 반차만 남은 경우, 동률 추첨.
- 08:49 경계, 미래 예약·변경·취소, 주말·휴일, 휴일 취소 후 신청 보존.
- 08:49:10부터 누락 복구, 동시 스케줄 호출, 저장 결과 재사용, 명부 변경 적용일.
- 식당 근무자 차단과 다른 활동 참여 허용, 미설정 직군 기본 거절, 관리자 우회·변조된 배정 결과 차단.
- 인원 제한 없는 직원·활동 등록, 나눔가게 시간대 선호도의 직원별 유지·다른 활동과의 분리, 공통 휴가, 단일 직군 변경과 활동 규칙 이력, 확정 배정 보존, 브라우저 언어와 관계없는 날짜 전달.
- 실제 Spring Security 폼 로그인·권한 분리·CSRF·본인 신청만 변경, 기능별 Thymeleaf 화면 응답과 REST 배정 결과 공개.
- 관리자 입력·직원 휴가/선호도·지원표·YAML 배분 설정부터 실제 배정까지 이어지는 기능 흐름과, 상대 직원 로그인·수락에 따른 지원일 맞교환.

배정 규칙 테스트는 프레임워크 없이 실행하고, 통합 테스트는 Flyway로 생성한 별도 인메모리 H2를 사용합니다.

공식 버전 요구사항: [Spring Boot 3.5 시스템 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html).

## 로그인 로고 글꼴

로그인 박스 위의 장복인간 워드마크는 [Jua](https://github.com/google/fonts/tree/main/ofl/jua)를 사용합니다. 필요한 글자용 WOFF2를 로컬 정적 파일로 포함하며 페이지에서 외부 글꼴 서버를 호출하지 않습니다. SIL Open Font License는 `src/main/resources/static/fonts/Jua-OFL.txt`에 함께 보관합니다.

## 직원 이름 로그인

직원은 `Employee.name` 하나로 표시 이름과 로그인 식별자를 함께 관리합니다. Spring Security 폼도 `name` 파라미터를 사용합니다. 직원은 Long 자동 증가 ID로 구분합니다. 휴가·선호도·배정도 같은 숫자 직원 ID를 참조합니다. 관리자 로그인 이름은 기존 `APP_ADMIN_USERNAME` 설정(기본 `admin`)을 유지합니다.


## 관리자 화면과 휴가 일정

화면의 서비스명은 장복인간입니다. 관리자 화면은 로그인과 같은 올리브·크림 테마를 사용하며, 상단 ☰ 메뉴에서 휴가 일정 / 직원 / 업무 / 휴일 / 나눔가게 활동 / 활동으로 이동합니다. 선택한 기능만 렌더링합니다.

직원은 본인 근무 달력에서, 관리자는 휴가 일정에서 직원과 날짜를 선택해 정상 출근·연차·병가·오전 반차·오후 반차를 저장합니다. 연차와 병가는 종일 제외하고, 오전 반차는 오후만, 오후 반차는 오전만 가능합니다. 출근 상태는 오전 반차 MORNING_HALF_DAY와 오후 반차 AFTERNOON_HALF_DAY로 구분합니다.

관리자용 `PUT /admin/employees/{employeeId}/leave/{date}`는 ADMIN만 허용하며, 직원용 `PUT /leave/{date}`는 로그인한 본인에게만 저장합니다. 두 경로 모두 주말·휴일·근무 기간·08:49 마감·확정 배정 변경 금지를 동일하게 검사합니다.

## 직원 프로필과 사진

직원 카드는 사진·이름·직군과 가입 활동을 보여주며, 누르면 프로필 상세를 엽니다. 직원은 햄버거 메뉴의 내 프로필 또는 직원 프로필로 접근합니다. 담당 업무는 활동 가입 내역을 표시하며 관리자는 활동 메뉴에서 담당 직원을 지정합니다.

프로필 사진은 본인 또는 관리자만 등록합니다. 2MB 이하의 JPG·PNG·HEIC(HEIF)를 최대 512px JPEG로 변환해 별도 employee_photo 테이블에 저장합니다. 원본 파일명이나 촬영 메타데이터는 보관하지 않습니다. 사진 조회에도 로그인이 필요하며 일반 배정·명부 조회에는 사진 데이터를 로드하지 않습니다. Docker 이미지에는 HEIC 변환기인 `heif-thumbnailer`가 포함됩니다. IDE나 JAR로 직접 실행할 때는 HEIC를 사용하려면 변환기가 `PATH`에 있어야 합니다.

## 직군 선택과 비밀번호 표시

직군은 식당 / 운영지원 / 운영지원(기계실) / 3층 / 발달지원 중 하나를 선택합니다.

직원은 일반 화면 메뉴의 **내 프로필 → 비밀번호 변경**에서 현재 비밀번호를 확인한 뒤 새 비밀번호를 저장할 수 있습니다.

직군 정의는 `WorkGroupType` enum 한 곳에서 관리합니다. 직원의 현재 직군 하나를 enum 이름으로 저장하고, 코드로 정의한 참여 제한도 enum으로 비교합니다. 직원의 직군 변경 이력은 저장하지 않습니다. 별도의 `WorkGroup` 엔티티·직군 마스터 테이블·Repository는 사용하지 않습니다.

로그인 및 직원 등록에는 작은 눈 아이콘이 있으며 처음에는 비밀번호를 가립니다. 저장된 비밀번호는 BCrypt 해시로 유지하고 입력 중인 비밀번호의 표시만 전환합니다.

## 활동 앨범과 나눔가게 날짜 카드

활동은 `Activity` enum에서 정의합니다. 활동 마스터 테이블이나 Flyway 활동 등록 데이터는 없습니다. 관리자 활동 탭은 enum의 활동을 앨범으로 보여주고, 세부 화면에서는 담당 직원만 관리합니다. 이름과 설명은 enum에서 읽습니다. 활동 추가 UI·등록 API·직군별 허용/불가 토글 API는 제거했습니다. 활동·직원 조합으로 가입 한 건을 저장하고 가입·탈퇴는 즉시 반영합니다. 당일 후보는 가입자 중 근무 기간·휴가·지원을 반영해 계산하며 확정 배정은 유지합니다. 식당으로 직군이 바뀌면 나눔가게 가입·선호도를 같은 트랜잭션에서 삭제하며, 원래 직군으로 돌아와도 자동 복구하지 않습니다.

나눔가게 활동은 오늘이 가운데 오는 가로 날짜 카드입니다. 공개 전에는 참여 직원·종일 휴가 직원·오전/오후 예정 인원과 서버 시각에 맞춘 카운트다운을 표시합니다. 스케줄러는 08:49:10에 배정하고, 화면은 08:50에 GET /api/sharing-shop/days/{date}로 저장된 결과를 자동으로 조회합니다. 결과가 아직 없으면 준비 중으로 표시하고 다시 조회합니다. 휴일은 배정 없이 쉬는 날로 표시합니다.

휴일 탭은 독립된 월별 달력입니다. 날짜를 선택하고 아래에서 휴일을 추가하며 사유는 비워둘 수 있습니다. 등록한 휴일은 빨간색으로 표시합니다.

직군은 **관리자 → 직원 → 직원 관리**(`/admin/employees/{id}`)에서 변경합니다. 담당 활동은 **관리자 → 활동**에서 변경합니다. 일반 직원 프로필(`/employees/{id}`)에서는 직군과 담당 활동을 조회합니다. 관리자 상세 URL과 변경 POST 요청은 Spring Security의 `ADMIN` 권한으로 보호합니다.

직원 로그인 후에는 `/sharing-shop`의 날짜별 나눔가게 카드가 열립니다. 직원 화면의 오른쪽 햄버거 메뉴에서 **나눔가게 활동 / 내 휴가 일정 / 내 식당지원 일정 / 직원 프로필 / 내 프로필**로 이동합니다. 나눔가게 카드는 관리자와 직원이 같은 화면·REST API를 사용하며, 휴가 달력은 `/my-leave`로 구분합니다. 직원은 로그인한 자신의 연차·병가·반차만 입력하고, 관리자는 관리자 휴가 일정에서 직원별로 대신 입력할 수 있습니다. 두 화면은 같은 휴가 기록과 마감 규칙을 사용합니다. 나눔가게 시간대 선호도는 나눔가게 화면에서 저장합니다. 날짜별 입력 없이 유지되며 주말·휴일·당일 지원과 관계없이 변경할 수 있습니다. 이미 확정된 배정은 유지합니다.

관리 화면은 `/admin` 주소를 직접 입력해 접근합니다. 관리자와 직원 모두 로그인 성공 시 `/sharing-shop`으로 이동하며, 로그인 전에 열려던 주소가 `/admin`이었더라도 관리자 화면으로 자동 이동하지 않습니다. 일반 화면에는 관리자 메뉴·진입 링크·권한 배지를 표시하지 않습니다. `/admin`과 `/admin/**`는 서버에서 `ADMIN`만 허용하므로, 직원이 직접 URL이나 변경 요청을 보내면 403입니다. 관리자용 나눔가게 카드는 `/admin/sharing-shop`, 직원 관리는 `/admin/employees/{id}`에서 제공하며 일반 화면과 메뉴를 구분합니다.

## 식당지원표·교환

- `/admin/cafeteria-support`: 월별 식당지원표를 관리합니다. 식당지원은 추첨하지 않고 관리자가 입력한 명단으로 운영하는 활동입니다. 기본 하루 2명이며 필요 인원을 날짜 효력으로 변경할 수 있습니다. 하루 명단은 식당지원에 가입한 직원 중 여러 명을 선택하며 월별로 한 번에 저장합니다. 같은 날 이미 등록되었거나 연차·병가·반차인 직원은 지원자로 저장할 수 없습니다. 미입력·인원 불일치는 표와 나눔가게 카드에 경고합니다. 명단을 입력하기 전에는 특정 직원을 임의로 제외하지 않습니다.
- `/cafeteria-support`: 직원 자신의 지원 일정과 교환 요청함입니다. 본인의 지원일을 선택하고 같은 지원 업무를 맡은 다른 직원의 다른 지원일과 맞교환을 요청합니다. 상대방 수락 전에는 명단이 바뀌지 않습니다. 수락 시 두 날짜를 하나의 트랜잭션으로 변경하고, 해당 슬롯의 나머지 요청은 무효화합니다. 관리자 표 수정, 휴가·직군·지원 중복과 마감도 다시 검사합니다.
- `/cafeteria-support/schedule`: 직원과 관리자가 일반 화면에서 월별 식당지원 날짜와 담당자를 확인합니다. 직원은 본인 교환 화면으로 이동할 수 있습니다.
- 휴가 입력과 지원표·교환 수락은 **당일 08:49 전**까지입니다. 나눔가게 시간대 선호도는 다음 추첨부터 반영합니다. 지원 필요 인원을 마감 이후 변경하면 다음 날부터 적용합니다. 교환 마감은 두 날짜 중 이른 날짜의 08:49입니다.
- 매일 한국 시간 **09:00**에 `CafeteriaSupportExchangeCleanupScheduler`가 만료 요청을 DB에서 삭제합니다. 마감 시각부터 조회·수락에서 만료로 처리합니다.

`CafeteriaSupportAssignment`(날짜별 지원), `CafeteriaSupportRequirement`(필요 인원 이력), `CafeteriaSupportExchangeRequest`(상대방 동의)를 분리했습니다. `RosterReader`는 날짜별 식당지원 제외를 한 번 반영해 자동 추첨과 카드가 같은 가능 인원을 사용합니다. 영구 활동 명부는 날짜별 지원 때문에 변경하지 않습니다.

식당지원은 하나의 활동이며 식당 외 직군은 계정을 등록할 때 자동 가입합니다. 나눔가게는 배정할 때 당일 식당지원 담당자를 후보에서 뺍니다. 식당지원 마감은 `app.cafeteria-support-cutoff`에서 관리합니다.

## 나눔가게 오전·오후 배분

`src/main/resources/application.yml`의 기존 `app` 아래에서 `sharing-shop-quotas: {}`를 바꿉니다. 다음 예시는 가능 인원 5명의 기본 방향을 오전 2명·오후 3명으로 둡니다.

```yaml
app:
  sharing-shop-quotas:
    5:
      morning: 2
      evening: 3
```

2명 이상부터 설정할 수 있으며 오전·오후 합계는 가능 인원수와 같아야 합니다. 실제 배정은 반차·기계실 상주·선호도에 따라 달라집니다. 정상 출근자가 한 명뿐이면 두 시간대를 모두 맡습니다. 새 설정은 앱을 다시 실행한 뒤 예정 인원과 다음 추첨에 적용됩니다.

엔티티 ID와 개발 DB 변경 원칙은 [엔티티·개발 DB](docs/persistence.md)를 참고하세요.

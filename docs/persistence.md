# 엔티티와 개발 DB

- 엔티티의 단순 getter는 `@Getter`, JPA 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 작성한다. 변경은 검증을 포함하는 도메인 메서드로 제공한다.
- 자동 생성 대리키는 `Long`과 `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 사용한다. 기존 엔티티를 조회·수정하거나 새 객체를 생성한 뒤 Repository `save()`로 저장한다. 네이티브 upsert나 `EntityManager.persist()` 직접 호출은 추가하지 않는다. 기존 엔티티를 수정할 때는 ID를 유지한다.
- 휴일 날짜 같은 자연키는 유지한다. 직원 사진은 직원 ID를 공유한다.
- 식당지원 명단은 `cafeteria_support_assignment`에 날짜·직원 고유 제약으로, 필요 인원은 `cafeteria_support_requirement`에 적용일 고유 제약으로, 맞교환 요청은 `cafeteria_support_exchange_request`에 저장한다.
- 활동 종류는 `activity.Activity` enum으로 정의한다. 활동 마스터 테이블·Repository·DB 참여 허용 규칙을 두지 않는다. 가입·업무·지원의 `activityCode` 필드는 `@Enumerated(EnumType.STRING)`으로 enum 이름을 저장한다. 활동 이름과 설명은 enum에서 제공한다.
- 직군은 `WorkGroupType` enum 하나로 관리한다. `Employee.workGroup`은 `@Enumerated(EnumType.STRING)`으로 enum 이름을 기본 매핑 컬럼인 `work_group`에 저장한다. 직원의 현재 직군은 한 개이며 변경하면 즉시 덮어쓴다. 직군 마스터·직군 변경 이력 테이블과 Repository는 두지 않는다. 확정 배정 결과는 별도로 보존한다.
- 나눔가게 시간대 선호도는 `sharing_shop_time_preference`에 저장한다. `employee_id`에 고유 제약을 적용해 직원별 한 건을 유지하며 날짜·시각·활동 코드 컬럼을 두지 않는다.
- 컬렉션과 이미지는 방어적 복사가 필요한 조회 메서드를 유지한다.
- `ActivityRegistration`은 활동과 직원의 가입 한 건이다. `activity_registration`의 `UNIQUE(activity_code, employee_id)`와 직원 외래키로 중복 가입·잘못된 직원 참조를 막는다. 활동은 enum으로 제한한다. 가입·탈퇴는 즉시 반영하고 동일 가입을 반복 저장하면 기존 ID를 유지한다. 적용일·명단 Set·명단 이력 테이블을 두지 않는다. 당일 후보는 가입자를 기준으로 계산하고 확정 배정은 `WorkTask`·`TaskAssignment`에 보존한다.

## 스키마 변경

`V1__initial_schema.sql`이 첫 운영 배포 스키마다. 운영 DB에 적용된 뒤 스키마를 바꿀 때는 새 Flyway 버전을 추가하고 필요한 기존 데이터를 같은 버전에서 이관한다. 적용된 버전 파일은 수정하지 않는다. 같은 번호의 파일을 두 위치에 함께 추가한다.

- MySQL: `src/main/resources/db/mysql/V{번호}__{설명}.sql`
- 테스트 H2: `src/test/resources/db/migration/V{번호}__{설명}.sql`

두 파일은 같은 결과 스키마를 만들어야 하며 문법만 각 DB에 맞춘다. 다른 프로젝트의 DB는 대상이 아니다.

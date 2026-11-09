# 업무 규칙 기능 테스트

기준일: 2026-11-07. [업무 규칙](business-rules.md)을 실제 입력과 결과로 검증한다. 테스트 숫자는 JUnit 실행 횟수이며, 서로 다른 기능 개수와 같지 않다.

## 기존 83회의 의미

- 기능·통합 시나리오 60회: 로그인·휴가·배정·직군·지원표·맞교환·화면 응답 등을 실제 Spring 서비스와 DB로 검증했다.
- 배분 로직 17회: 인원수별 반복 케이스와 휴가·반차·선호도 조합을 검증했다.
- 참여 제한 3회: 식당 직군 차단과 참여 정책을 검증했다.
- DB 이관 3회: 기존 데이터와 배정 기록이 보존되는지 검증했다.

기존의 큰 `WorkShareApplicationTest`는 업무별 기능 테스트 7개 클래스로 나누었다. 기존 60개 시나리오는 유지하고 한글 `@DisplayName`을 붙였다. 여기에 실제 로그인 세션으로 진행하는 전체 기능 흐름 10회와, 전역 락 제거 후 동시 저장·대상 행 잠금을 검증하는 기능 7회를 추가했다. 로그인 세션의 직원 ID를 기준으로 휴가·선호도·프로필 대상을 결정하고 조작한 대상 ID를 무시하는 실제 로그인 시나리오도 추가했다.

## 현재 실행 결과

**기본 총 125회 통과: 기능·통합 95회 + 보조 검증 30회.** MySQL 선택 실행 테스트는 이 숫자에 포함하지 않는다.

| 구분 | 테스트 파일 | 실행 횟수 |
| --- | --- | --- |
| 실제 로그인부터 결과 조회까지의 전체 기능 흐름 | [BusinessRulesAcceptanceTest](../src/test/java/com/jangbokhuman/workshare/integration/BusinessRulesAcceptanceTest.java) | 10 |
| 동시 저장·대상 행 잠금·중복 수락 | [ConcurrencyFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/ConcurrencyFeatureTest.java) | 4 |
| 활동·직원별 가입과 당일 배정 분리 | [ActivityRegistrationFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/ActivityRegistrationFeatureTest.java) | 5 |
| 직원별 나눔가게 시간대 선호도 | [SharingShopTimePreferenceFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/SharingShopTimePreferenceFeatureTest.java) | 4 |
| 휴가·근무일·마감 | [LeaveFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/LeaveFeatureTest.java) | 7 |
| 배정·설정·확정 기록 | [AllocationFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/AllocationFeatureTest.java) | 20 |
| 직원·직군·프로필 | [EmployeeFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/EmployeeFeatureTest.java) | 12 |
| 지원표·맞교환 | [SupportFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/SupportFeatureTest.java) | 11 |
| 로그인·권한·본인 확인 | [SecurityFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/SecurityFeatureTest.java) | 9 |
| enum 활동·가입·참여 제한 | [ActivityFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/ActivityFeatureTest.java) | 7 |
| 활동 전용 배정기와 공통 추가 업무 분리 | [ActivityAllocatorFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/ActivityAllocatorFeatureTest.java) | 1 |
| 메뉴·전체 휴가 일정·휴일 달력·결과 공개 | [NavigationFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/NavigationFeatureTest.java) | 5 |
| 출근 상태별 오전·오후 배정과 근무 시간 | [AttendanceTest](../src/test/java/com/jangbokhuman/workshare/leave/AttendanceTest.java) | 5 |
| 인원 배분·선호도 정책 | [PreferenceAllocationPolicyTest](../src/test/java/com/jangbokhuman/workshare/activity/sharingshop/PreferenceAllocationPolicyTest.java) | 19 |
| 추가 업무 시간대·배정 제약 | [RandomTaskAllocationPolicyTest](../src/test/java/com/jangbokhuman/workshare/activity/sharingshop/RandomTaskAllocationPolicyTest.java) | 3 |
| 활동 가입 자격 정책 | [ActivityRegistrationPolicyTest](../src/test/java/com/jangbokhuman/workshare/activity/ActivityRegistrationPolicyTest.java) | 3 |

## 전체 기능 흐름 테스트

[BusinessRulesAcceptanceTest](../src/test/java/com/jangbokhuman/workshare/integration/BusinessRulesAcceptanceTest.java)는 Given → When → Then 흐름으로 다음을 확인한다.

| 주어진 상황 | 사용자 동작·실행 | 확인하는 결과 | 실행 횟수 |
| --- | --- | --- | --- |
| 정상 출근자 0~5명, 휴가·선호도 미입력 | 관리자 직원 등록 → 실제 로그인 → 08:49:10 배정 → 08:50 REST 조회 | YAML 설정과 기본 비율, 1명 겸임, 별도 상주 인원 미제외, 재실행 시 동일 결과 | 6 |
| 일반 직원 6명 + 식당 직원 1명, 식당지원 2명, 연차 1명, 반차 1명 | YAML에서 3명 기준 1/2 설정 → 관리자가 지원표 입력 → 직원이 휴가·오후 선호 신청 → 배정 → 서비스에서 추가 업무 등록 | 실제 가능 인원 3명, 반차자 오전 1명, 오후 선호자 2명, 지원·연차·식당 직군 제외, 추가 업무 후 기존 배정 보존 | 1 |
| 홍길동 오늘 지원, 김민수 내일 지원 | 홍길동이 요청 → 제3자 수락 시도 → 김민수가 로그인해 08:49 직전 수락 | 수락 전 명단 유지, 제3자 403, 두 날짜의 지원자·활동 제외 인원 동시 변경, 재수락 불가 | 1 |
| 당일 지원표와 휴가가 있는 직원 | 08:49 직전 휴가와 지원표 저장 → 08:49에 변경 시도 | 휴가와 지원표는 08:49부터 거절, 나눔가게 시간대 선호도는 계속 변경 가능, 실패 요청이 기존 기록을 바꾸지 않음 | 1 |
| 내일 연차를 미리 신청한 직원 | 관리자가 사유 없는 휴일 등록 → 직원 신청 시도 → 관리자 휴일 해제 → 직원 연차 취소 → 주말 배정 실행 | 휴일 신청 차단·OFF 표시, 기존 연차 보존, 정상 출근으로 취소, 주말에는 배정 없음 | 1 |

## 규칙별로 찾기

| 업무 규칙 | 주요 기능 테스트 |
| --- | --- |
| 한글 이름 로그인·이름 중복·비밀번호·직군 단일 선택·담당 업무 복수 선택·사진 권한 | `EmployeeFeatureTest`, `SecurityFeatureTest` |
| 직원 본인 입력·관리자 대리 휴가·08:49 마감·미래 예약·취소·주말·휴일 | `LeaveFeatureTest`, `BusinessRulesAcceptanceTest` |
| 식당 직군 금지·영구 활동 명부와 날짜별 제외 구분·나눔가게 전용 시간대 선호도 | `ActivityFeatureTest`, `AllocationFeatureTest`, `SupportFeatureTest` |
| 0~5명 기본 배분·1명 겸임·정원 제한 없음·설정 배분·오전·오후 반차의 시간대 제한·선호도 추첨 | `AllocationFeatureTest`, `BusinessRulesAcceptanceTest`, 배분 정책 테스트 |
| 월별 지원표·필요 인원 변경·다른 제외 사유·지원 중복·지원 당일 08:49 마감 | `SupportFeatureTest`, `BusinessRulesAcceptanceTest` |
| 본인 요청·상대 수락·두 날짜 동시 교환·만료·관리자 수정·동시 수락·휴가 충돌·정리 | `SupportFeatureTest`, `BusinessRulesAcceptanceTest` |
| 여러 업무 추가·추가 업무만 배정·확정 결과 보존·중복 추첨 금지 | `AllocationFeatureTest`, `BusinessRulesAcceptanceTest` |
| 관리자 URL 직접 접근·직원 403·일반 화면 관리자 메뉴 미노출 | `SecurityFeatureTest` |
| 햄버거 메뉴·날짜 카드 HTML·08:50 REST 결과 공개·준비 중·휴일 | `NavigationFeatureTest` |

## 업무별 상세 시나리오

각 항목은 테스트의 한글 표시명과 해당 메서드다. 반복 인원수 케이스는 한 메서드에서 여러 회 실행될 수 있다.

### 활동·직원별 가입

[ActivityRegistrationFeatureTest.java](../src/test/java/com/jangbokhuman/workshare/integration/ActivityRegistrationFeatureTest.java)

- 직원 계정만 만들면 미가입이며, 가입 후 연차를 써도 가입 정보는 유지한다.
- 활동·직원 고유 제약으로 중복을 막고 반복 저장 시 같은 가입 ID를 유지한다.
- 추첨 후 탈퇴·재가입은 즉시 반영하고 확정 배정은 보존하며 이전 선호도를 복구하지 않는다.
- 3층에서 식당으로 옮기면 나눔가게 가입·선호도를 삭제하고 확정 배정은 유지한다. 직군을 되돌려도 자동 재가입하지 않는다.
- 가입과 식당 이동이 동시에 발생해도 식당 직원의 가입이 남지 않는다.

### 직원별 나눔가게 시간대 선호도

[SharingShopTimePreferenceFeatureTest.java](../src/test/java/com/jangbokhuman/workshare/integration/SharingShopTimePreferenceFeatureTest.java)

- 선호도가 날짜가 바뀌어도 유지되고 변경 후 다음 추첨에 반영되며 확정 배정은 보존된다: `employeeTimePreferencePersistsAcrossDaysAndOnlyAffectsFutureDraws`
- 휴일·주말·당일 식당지원 여부와 관계없이 같은 직원의 선호도 한 건을 유지·변경한다: `timePreferenceIsIndependentOfDailyHolidaysAndRestaurantSupport`
- 근무 시작 전 직원도 최신 담당 명부에 등록되면 선호도를 저장하지만 실제 당일 배정에서는 제외된다. 명부에 없으면 저장을 거절한다: `registeredFutureEmployeeCanChangeTimePreferenceWithoutBecomingADailyParticipant`
- 내일부터 제외되는 담당 직원은 오늘 마감 이후에도 선호도를 변경할 수 있고 실제 비활성 시작일부터 변경을 거절한다: `timePreferenceChangesAreDeniedStartingOnInactiveFromRatherThanRosterEffectiveDate`

### 휴가·근무일·마감

[LeaveFeatureTest.java](../src/test/java/com/jangbokhuman/workshare/integration/LeaveFeatureTest.java)

- 휴가는 당일 08:49부터 마감하고 미래 예약과 취소를 허용한다: `exactCutoffAndFutureBookingsAndCancellation`
- 토·일요일과 등록 휴일에는 휴가 신청과 배정을 하지 않는다: `weekendsAndCustomHolidaysSkipBothRequestsAndAllocation`
- 휴일을 해제하면 보관한 휴가·선호도가 다시 적용된다: `removingFutureHolidayPreservesEarlierBookings`
- 브라우저 언어와 무관하게 선택한 날짜에 신청을 저장한다: `datesInFormsAndRedirectsStayIsoAcrossBrowserLocales`
- 직원은 본인 휴가만 수정하고 관리자는 직원 휴가를 대신 입력한다: `adminCanManageEmployeeLeaveAndStaffCanOnlyManageTheirOwnLeave`
- 관리자 대리 신청에도 마감·휴일을 적용하고 병가는 모든 업무에서 제외한다: `adminLeaveRespectsCutoffWeekendsAndExistingAllocationAndSickLeaveExcludesEveryTask`
- 다른 직원 ID를 보내도 본인 달력만 바뀌며 관리자 대리 입력이 반영된다: `staffCalendarAlwaysUsesOwnIdentityAndReflectsAdminLeaveChanges`

### 배정·설정·확정 기록

[AllocationFeatureTest.java](../src/test/java/com/jangbokhuman/workshare/integration/AllocationFeatureTest.java)

- 08:49:10 전에는 배정하지 않고 재시도해도 확정 배정을 유지한다: `dueTimeRecoveryAndRepeatedCallsKeepPersistedResultsIncludingAdditionalTasks`
- 자동 배정 서비스를 동시에 호출해도 한 세트만 저장되며, 충돌한 호출은 롤백될 수 있다: `concurrentAllocationCallsProduceOnlyOneSavedPlan`
- 기본 소집일은 계정 등록일이고 마감 후 관리자 인원 제외는 다음 날 적용한다: `registrationDefaultsToTodayAndRetirementAfterCutoffTakesEffectTomorrow`
- 같은 날 추가 업무가 늘어나도 이전 업무 배정을 보존한다: `severalLaterTasksKeepAllEarlierAssignmentsAndEachOthersResults`
- 미래 업무 등록이 휴가 입력을 막지 않고 해당 근무일에 배정된다: `futureTasksDoNotLockLeaveInputAndAreAllocatedIndividuallyOnTheirDay`
- 08:49 이후 추가한 업무는 추첨을 기다리며 휴가 마감은 연장하지 않는다: `betweenCutoffAndDrawNewWorkWaitsButDoesNotChangeTheLeaveDeadline`
- 대기 중 추가 업무는 취소할 수 있고 휴일에는 배정하지 않는다: `pendingTaskCanBeCancelledAndHolidaysPausePendingWork`
- 업무 입력 조건을 검사하고 가능한 직원이 없으면 부족을 표시한다: `taskValidationAndNoEligibleStaffAreHandledExplicitly`
- 동시에 추가 업무를 등록해도 오전·오후를 다시 추첨하지 않는다: `concurrentAdditionalWorkDoesNotTriggerAnotherShiftDraw`
- 관리자 웹에서 추가 업무를 등록·취소하는 경로를 제공하지 않는다: `adminCannotManageAdditionalWorkThroughTheWeb`
- 식당 직군을 먼저 제외한 인원으로 기본·추가 업무를 배정한다: `shiftQuotasAndAllAdditionalWorkExcludeRestaurantStaffBeforeCounting`
- 추가 업무 필요 인원을 10명으로 제한하지 않으며 부족 인원을 표시한다: `extraTasksCanRequestMoreThanTenWorkersAndReportTheShortfall`
- 오전·오후 반차를 입력하면 선호와 관계없이 가능한 시간대의 기본·추가 업무만 배정한다(2회): `bothHalfDayTypesRestrictEveryAllocationPath`
- 반차자가 많으면 YAML 목표 비율을 조정해 모든 직원을 배정한다: `customQuotaAdjustsToKeepAllHalfDayWorkersAssigned`
- 기계실 상주 가능 여부를 오전·오후별로 확인하고, 상주가 불가능해도 나눔가게에 배정한 뒤 해당 시간대에 경고를 표시한다(2회). `facilityStaffCoverBothShiftsAndWarnIfNobodyCanStay`
- 유일한 기계실 반차자가 상주하면 다른 직군의 종일 근무자가 겸임하고, 기계실 출근자가 없는 반대 시간대만 경고한다(2회). `fullDayWorkerCoversBothShiftsSoHalfDayFacilityWorkerCanStay`
- 두 명 다 오전만 가능하면 일반 직원이 나눔가게를 맡고 기계실 직원은 오전에 상주한다. `aMorningOnlyFacilityWorkerStaysWhenAnotherWorkerCanCoverTheSameShift`
- 배정 정책은 전원 오전 가능 3대0, 오전만 가능한 직원 6명과 종일 근무자 1명의 6대1, 선호에 따른 2대3, 기계실 직원 한 명의 겸임과 경고를 검증한다.

### 직원·직군·프로필

[EmployeeFeatureTest.java](../src/test/java/com/jangbokhuman/workshare/integration/EmployeeFeatureTest.java)

- 직원 수를 제한하지 않고 입사·제외 후에도 확정 배정을 보존한다: `employeesCanExceedTenAndRosterChangesPreserveCompletedAssignments`
- 한글 이름으로 로그인하며 앞뒤 공백과 한글 표기를 정규화한다: `koreanNameIsBothDisplayNameAndLoginAndNormalizesWhitespaceAndHangul`
- 직원 이름 중복과 잘못된 한글 이름 등록을 거절한다: `duplicateNamesAreRejectedAndNonKoreanNewNamesAreNotAccepted`
- 직원 비밀번호는 허용 문자와 8~20자 제한을 검사한다: `employeePasswordUsesAsciiCharactersAndLengthLimits`
- 발달지원 직원을 등록하고 본인만 비밀번호를 변경한다: `onlyEmployeesCanChangeTheirOwnPassword`
- 기존 비밀번호는 새 등록 규칙과 달라도 로그인할 수 있다: `existingPasswordsOutsideTheNewRegistrationRulesCanStillLogin`
- 동시에 같은 이름을 등록해도 계정 하나만 생성된다: `concurrentSameNameRegistrationOnlyCreatesOneAccount`
- 프로필 카드로 직원을 조회하고 관리자는 여러 담당 업무를 지정한다: `employeeCardsLinkToProfilesAndAdministratorsCanAssignMultipleResponsibilities`
- 본인 프로필이라도 직군·담당 업무는 관리자 상세 화면에서만 바꾼다: `workGroupAndResponsibilitiesAreEditableOnlyInsideAdminEmployeeManagement`
- 프로필 사진은 본인·관리자만 등록하고 실제 이미지와 용량을 검사한다: `profilePhotosAreOwnerOrAdminOnlyAndMustBeRealImages`
- 직군은 정해진 목록 중 하나이며 담당 업무는 여러 개 선택한다: `groupsAreSingleChoiceAndEmployeeResponsibilitiesAreMultipleValidatedChoices`
- 관리자가 담당 업무 목록을 추가하고 중복 이름은 거절한다: `administratorsCanAddSelectableResponsibilitiesAndDuplicateTitlesAreRejected`

### 지원표·맞교환

[SupportFeatureTest.java](../src/test/java/com/jangbokhuman/workshare/integration/SupportFeatureTest.java)

- 기존 식당지원 사유로도 나눔가게 미가입 직원을 등록하고 지원일을 교환한다. `cafeteriaSupportDoesNotRequireSharingShopMembership`

- 식당지원자를 기본 업무와 추가 업무의 자동 배정에서 제외한다: `scheduledSupportIsExcludedFromAutomaticShiftAndExtraTaskAssignments`
- 제외 사유를 추가해도 활동 가입은 유지하며 날짜별 지원 중복은 막는다: `exclusionReasonsPreserveActivityMembershipAndPreventDoubleBooking`
- 휴가와 지원표는 08:49에 함께 마감한다: `supportAndLeaveCloseAtEightFortyNine`
- 상대방 수락 전에는 표를 유지하고 수락하면 두 지원일을 함께 교환한다: `supportExchangeRequiresRecipientConsentAndSwapsBothDatesAtomically`
- 만료 요청은 수락할 수 없고 정리 시 미만료 요청과 지원표는 보존한다: `expiredExchangesCannotBeAcceptedAndDailyCleanupRetainsFutureRequests`
- 관리자 표 수정은 관련 요청을 무효화하며 동시 수락은 한 번만 처리한다: `adminRosterEditsInvalidateRelatedRequestsAndConcurrentAcceptsOnlySucceedOnce`
- 수락할 때 새 휴가·다른 지원 충돌을 다시 검사하고 상대는 거절할 수 있다: `exchangeAcceptanceRechecksNewLeaveAndOtherSupportConflictsAndCanBeRejected`
- 두 직원 모두 맡을 수 있는 날짜만 교환 후보로 보여준다: `exchangeChoicesOnlyShowDatesBothEmployeesCanActuallyTake`
- 월별 지원표는 한 번에 저장·롤백하고 관리자와 직원 접근 권한을 구분한다: `monthlySupportSaveIsTransactionalAndSupportPagesAreRoleProtected`
- 직원과 관리자는 일반 화면에서 월별 식당지원표를 조회한다. 직원은 맞교환 화면으로 이동할 수 있고, 날짜별 실제 지원 인원은 기준과 다를 수 있다. `staffAndAdministratorsCanReadTheMonthlyCafeteriaSchedule`

### 로그인·권한·본인 확인

[SecurityFeatureTest.java](../src/test/java/com/jangbokhuman/workshare/integration/SecurityFeatureTest.java)

- 직원 로그인은 세션이 없어도 유지되고 로그아웃과 비밀번호 변경을 반영한다: `staffLoginSurvivesSessionExpiry`

- 로그인·권한·CSRF를 검사하고 직원 입력은 본인에게만 반영한다: `authenticatedTemplatesAndRoleRestrictionsAndCsrfAndOwnRequests`
- 실제 로그인 세션의 직원 ID로 본인을 확인하며 휴가·선호도 요청의 타인 ID와 이름은 무시하고 내 프로필도 세션 ID로 연다: `authenticatedEmployeeIdDeterminesTheOwnerOfAllSelfRequests`
- 직원과 관리자 모두 실제 로그인 후 일반 나눔가게 화면으로 이동한다: `realFormLoginsAlwaysOpenTheGeneralSharingPage`
- 식당 직원도 로그인·휴가 신청은 가능하지만 나눔가게 선호도는 신청할 수 없다: `restaurantStaffCanLoginAndRequestLeaveButCannotSubmitSharingPreferences`
- 조작된 배정 결과를 거절하고 자동 배정에서 식당 직군을 제외한다: `forgedAllocationResultsAreRejectedAndAutomaticAllocationExcludesRestaurantWorkers`
- 나눔가게 시간대 선호도는 본인만 저장하고 CSRF 없는 요청은 거절한다: `preferenceEndpointsProtectIdentityAndCsrfAndActivitySelectionRenders`
- 관리자 계정이어도 일반 화면에는 관리자 진입 메뉴·배지를 표시하지 않는다: `generalPagesNeverExposeAdminNavigationEvenForAdminAccounts`
- 로그인 전 관리자 주소를 열어도 일반 화면으로 이동하며 직원의 직접 접근은 403이다: `loginNeverRedirectsToTheSavedAdminUrlAndDirectAdminAccessRequiresTheAdminRole`

### 활동 명부·참여 제한

[ActivityFeatureTest.java](../src/test/java/com/jangbokhuman/workshare/integration/ActivityFeatureTest.java)

- 식당 직군은 나눔가게 가입·배정을 할 수 없다: `restaurantWorkersCannotRegisterOrBeAssignedToSharingShop`
- 참여 가능한 직군도 활동 가입 후에만 당일 후보가 된다: `eligibleWorkersMustRegisterBeforeParticipating`
- 직군은 현재 값 하나를 덮어쓰고 확정 배정은 유지한다: `workGroupChangesReplaceTheCurrentGroupAndPreserveConfirmedAssignments`
- 활동 이름·설명은 enum에서 제공하고 활동 테이블·생성 API를 두지 않는다: `activityMetadataComesFromTheEnumAndHasNoCreationEndpoint`
- 활동 가입에 고정 정원을 두지 않는다: `registrationsHaveNoFixedHeadcountLimit`
- 관리자는 가입을 즉시 변경하며 확정 배정은 유지한다: `administratorsUpdateRegistrationsImmediatelyWithoutChangingConfirmedAssignments`
- 가입 직원이 없어도 enum에 정의된 활동 카드를 표시한다: `activitiesRemainVisibleWithoutAnyRegisteredEmployees`

### 활동별 자동 업무 분리

[ActivityAllocatorFeatureTest.java](../src/test/java/com/jangbokhuman/workshare/integration/ActivityAllocatorFeatureTest.java)

- 전용 배정기를 등록하지 않은 실행에서는 미리 등록한 대기 업무만 배정한다.

### 메뉴·휴일 달력·결과 공개

[NavigationFeatureTest.java](../src/test/java/com/jangbokhuman/workshare/integration/NavigationFeatureTest.java)

- 로그인한 직원과 관리자가 선택한 날짜의 직원별 휴가 종류를 조회한다. `teamLeaveCalendarShowsOnlyLeaveForTheSelectedDate`

- 관리자 메뉴는 선택한 기능만 보여주고 직원이 없어도 열 수 있다: `adminMenuRendersOnlyTheSelectedFeatureIncludingEmptyEmployeeState`
- 직원 햄버거 메뉴에서 날짜 카드·본인 휴가·프로필로 이동한다: `staffMenuOpensSharingCardsAndOwnLeaveCalendarAcrossStaffPages`
- 08:50 전에는 예정 인원만 보여주고 이후 저장된 배정 결과를 공개한다: `sharingApiPreviewsCountsAndRevealsPersistedAssignmentsAtEightFifty`
- 배정 지연은 준비 중으로 표시하고 토·일·사유 없는 휴일에는 배정하지 않는다: `sharingApiHandlesDelayedSchedulerWeekendsAndBlankHolidayReasons`

## 동시 저장·행 잠금 기능 검증

[ConcurrencyFeatureTest](../src/test/java/com/jangbokhuman/workshare/integration/ConcurrencyFeatureTest.java)의 4회는 다음을 확인한다.

- 본인·관리자가 동일 날짜의 첫 휴가를 동시에 저장하면 고유 제약으로 중복 행을 막고, 이후 수정은 기존 ID를 유지한다. 최초 등록 경합에서는 한 요청이 실패할 수 있다.
- 직원별 나눔가게 시간대 선호도의 최초 동시 저장도 고유 제약으로 중복을 막고 기존 엔티티 수정 시 ID를 유지한다.
- 여러 날짜의 휴가와 직원별 나눔가게 시간대 선호도를 동시에 저장해도 서로 덮어쓰지 않는다.
- 동일 지원일을 서로 다른 두 상대방이 동시에 교환 수락해도 한 교환만 반영된다.

[MySqlLockingIT](../src/test/java/com/jangbokhuman/workshare/integration/MySqlLockingIT.java)는 별도로 **실제 MySQL 3회**를 실행한다. JPA 최초 저장 경합과 기존 ID 보존, 겹치는 지원일 맞교환, 가입과 식당 이동의 동시 처리에 따른 가입·선호도 무결성을 검증한다. 테스트가 만든 직원·지원 사유·관련 기록만 정리한다.

잠금이 필요한 이유와 실제 대상은 [동시 저장과 잠금 기준](concurrency.md)에 정리한다.

## 실행 방법

전체 검증:

```bash
./mvnw test
```

전체 기능 흐름만 실행:

```bash
./mvnw -Dtest=BusinessRulesAcceptanceTest test
```

지원표·교환 기능만 실행:

```bash
./mvnw -Dtest=SupportFeatureTest test
```

별도 검증 DB 주소를 `MYSQL_TEST_URL`에 지정한 뒤 실제 MySQL 동시성 검증만 실행:

```bash
./mvnw -Dtest=MySqlLockingIT test
```

## 검증 범위와 한계

- MockMvc를 사용하는 서버 기능·통합 테스트다. HTTP 요청, Spring Security, 컨트롤러, 실제 업무 서비스, JPA, Flyway로 생성한 테스트 DB를 거친다.
- 새 전체 흐름 테스트는 `/login`으로 인증해 받은 세션을 사용한다. `user(...)`로 인증을 대신 주입하거나 업무 서비스·저장소의 반환값을 모킹하지 않는다. 나머지 기능 테스트 일부는 권한 분기를 위해 인증 사용자 주입 기능을 사용한다.
- 배경 스케줄러가 실제 시각에 임의로 실행되지 않도록 해당 진입 빈은 공통 환경에서 모킹한다. 전체 흐름 테스트는 실제 `AllocationScheduler` 객체의 진입 메서드를 호출해 실제 배정 서비스와 DB 저장을 검증한다.
- 마감 검증은 한국 시간의 고정 날짜와 조절 가능한 `Clock`으로 진행한다. 매번 실제 오전까지 기다리지는 않는다. 만료 요청 정리는 실제 정리 메서드를 실행해 삭제 결과를 확인한다.
- 기본 실행 DB는 기본 모드의 테스트 전용 H2다. 실제 MySQL의 JPA 저장·잠금 차이는 별도 `MySqlIntegrationIT`와 `MySqlLockingIT` 선택 실행 대상으로 구분한다. 기본 125회에 실제 MySQL 테스트가 포함된 것처럼 계산하지 않는다.
- HTML 메뉴와 카드 구조, REST 결과 공개 조건은 확인하지만 브라우저의 JavaScript 카운트다운·가로 스와이프·모바일 배치까지 자동 실행하는 테스트는 아니다.
- 30일 자동 로그인은 아직 구현하지 않은 논의 항목이므로 제공 기능이나 통과한 기능 테스트로 포함하지 않는다.


개발 단계의 DB 초기화 정책에 따라 데이터 보존용 이관 테스트 4회는 제거했다. 현재 업무 규칙 기능·통합 95회를 실행하며, 운영 MySQL과 테스트 H2 모두 현재 초기 스키마로 검증한다.

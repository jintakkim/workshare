package com.jangbokhuman.workshare.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.sharingshop.SharingShopTaskReader;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.RosterService;
import com.jangbokhuman.workshare.employee.WorkGroupType;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@DisplayName("직원 계정·직군·프로필")
class EmployeeFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("직원 수를 제한하지 않고 입사·제외 후에도 확정 배정을 보존한다")
    void employeesCanExceedTenAndRosterChangesPreserveCompletedAssignments() {
        List<String> names =
                List.of(
                        "홍길동", "김민수", "이서연", "박지훈", "최유진", "정하늘", "윤서준", "한지민", "서지우", "남지현", "강민준",
                        "유하린");
        names.forEach(this::register);
        registerRestaurant("강수진");
        assertThat(employeeRepository.count()).isEqualTo(13);
        assertThat(sharingShopWorkers(TODAY)).hasSize(12);
        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        List<SharingShopTaskReader.TaskView> before =
                sharingShopTasks(TODAY);
        assertThat(
                        before.stream()
                                .filter(t -> t.code().equals("MORNING"))
                                .findFirst()
                                .orElseThrow()
                                .assignees())
                .hasSize(6);
        assertThat(
                        before.stream()
                                .filter(t -> t.code().equals("EVENING"))
                                .findFirst()
                                .orElseThrow()
                                .assignees())
                .hasSize(6);
        rosterService.retireEmployee(employeeRepository.findByName("김민수").orElseThrow().getId());
        register("이도윤");
        rosterService.changeWorkGroup(
                employeeRepository.findByName("강수진").orElseThrow().getId(), "GENERAL");
        registerForActivity(Activity.SHARING_SHOP, "강수진");
        assertThat(sharingShopWorkers(TODAY)).hasSize(14);
        assertThat(sharingShopTasks(TODAY))
                .isEqualTo(before);
        assertThat(sharingShopWorkers(TODAY.plusDays(1)))
                .hasSize(13)
                .extracting(Worker::name)
                .contains("이도윤", "강수진")
                .doesNotContain("김민수");
        testClock.set(TODAY.plusDays(1).atTime(8, 50));
        dailyAllocationService.allocateTodayIfDue();
        List<SharingShopTaskReader.TaskView> tomorrow =
                sharingShopTasks(TODAY.plusDays(1));
        assertThat(
                        tomorrow.stream()
                                .filter(t -> t.code().equals("MORNING"))
                                .findFirst()
                                .orElseThrow()
                                .assignees())
                .hasSize(7);
        assertThat(
                        tomorrow.stream()
                                .filter(t -> t.code().equals("EVENING"))
                                .findFirst()
                                .orElseThrow()
                                .assignees())
                .hasSize(6);
    }

    @Test
    @DisplayName("본인만 소집일을 수정하며 소집해제 예정일 다음 날부터 배정 후보에서 제외한다")
    void ownServiceStartDateControlsWorkPeriod() throws Exception {
        now("10:00");
        register("홍길동");
        register("김민수");
        Long employeeId = employeeId("홍길동");
        String profile = "/employees/" + employeeId;
        String changeDate = profile + "/service-period";

        assertThat(rosterService.getEmployeeView(employeeId).activeFrom()).isEqualTo(TODAY);
        mockMvc.perform(get(profile).with(testUser("김민수", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"serviceStartDate\""))));
        mockMvc.perform(post(changeDate).with(testUser("김민수", "STAFF")).with(csrf())
                        .param("serviceStartDate", "2025-02-05"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(changeDate).with(testUser("홍길동", "STAFF")).with(csrf())
                        .param("serviceStartDate", "2025-02-05"))
                .andExpect(redirectedUrl(profile + "#service-period"));
        mockMvc.perform(get(profile).with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"2025-02-05\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-11-04")));
        assertThat(rosterService.getEmployeeView(employeeId).serviceEndDate()).isEqualTo(TODAY);
        assertThat(sharingShopWorkers(TODAY)).extracting(Worker::id).contains(employeeId);
        assertThat(sharingShopWorkers(TODAY.plusDays(1))).extracting(Worker::id).doesNotContain(employeeId);

        mockMvc.perform(post(changeDate).with(testUser("홍길동", "STAFF")).with(csrf()))
                .andExpect(redirectedUrl(profile + "#service-period"))
                .andExpect(flash().attribute("error", "소집일을 선택해주세요."));
        rosterService.retireEmployee(employeeId);
        mockMvc.perform(post(changeDate).with(testUser("홍길동", "STAFF")).with(csrf())
                        .param("serviceStartDate", "2026-11-04"))
                .andExpect(flash().attribute("error", "관리자가 업무 인원에서 제외한 직원은 복무 기간을 변경할 수 없습니다."));
        assertThat(rosterService.getEmployeeView(employeeId).serviceEndDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("종료 월에 같은 날짜가 없으면 윤년 여부를 반영해 그달 말일에 해제된다")
    void serviceEndsOnTheLastDayWhenTheAnniversaryDayIsMissing() {
        Employee employee = new Employee("홍길동", "unused", LocalDate.of(2026, 5, 31), WorkGroupType.GENERAL);
        assertThat(employee.getInactiveFrom()).isEqualTo(LocalDate.of(2028, 3, 1));
        employee.changeServicePeriod(LocalDate.of(2027, 5, 31), null, TODAY);
        assertThat(employee.getInactiveFrom()).isEqualTo(LocalDate.of(2029, 3, 1));
    }

    @Test
    @DisplayName("소집해제일은 직접 고칠 수 있으며 소집일보다 늦고 오늘보다 이르지 않아야 한다")
    void serviceEndDateCanBeEnteredDirectlyButMustFollowTheStartAndToday() throws Exception {
        register("홍길동");
        Long id = employeeId("홍길동");
        String changePeriod = "/employees/" + id + "/service-period";
        mockMvc.perform(post(changePeriod).with(testUser("홍길동", "STAFF")).with(csrf())
                        .param("serviceStartDate", "2025-01-05").param("serviceEndDate", "2026-11-03"))
                .andExpect(flash().attribute("error", "소집해제일은 오늘보다 이전으로 설정할 수 없습니다."));
        mockMvc.perform(post(changePeriod).with(testUser("홍길동", "STAFF")).with(csrf())
                        .param("serviceStartDate", "2025-01-05").param("serviceEndDate", "2024-12-31"))
                .andExpect(flash().attribute("error", "소집해제일은 소집일보다 늦어야 합니다."));
        mockMvc.perform(post(changePeriod).with(testUser("홍길동", "STAFF")).with(csrf())
                        .param("serviceStartDate", "2025-01-05").param("serviceEndDate", "2026-11-05"))
                .andExpect(flash().attribute("success", "복무 기간을 저장했습니다."));
        assertThat(rosterService.getEmployeeView(id).serviceEndDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(sharingShopWorkers(TODAY.plusDays(1)))
                .extracting(Worker::id).contains(id);
        mockMvc.perform(post(changePeriod).with(testUser("홍길동", "STAFF")).with(csrf())
                        .param("serviceStartDate", "2025-06-01").param("serviceEndDate", "2026-12-31"))
                .andExpect(flash().attribute("success", "복무 기간을 저장했습니다."));
        assertThat(rosterService.getEmployeeView(id).serviceEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        mockMvc.perform(post(changePeriod).with(testUser("홍길동", "STAFF")).with(csrf())
                        .param("serviceStartDate", "2025-01-05"))
                .andExpect(flash().attribute("error", "소집해제일은 오늘보다 이전으로 설정할 수 없습니다."));
        assertThat(rosterService.getEmployeeView(id).serviceEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        mockMvc.perform(get("/employees/" + id).with(testUser("홍길동", "STAFF")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"serviceEndDate\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/service-period.js")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("연장된 소집해제일"))));
    }

    @Test
    @DisplayName("한글 이름으로 로그인하며 앞뒤 공백과 한글 표기를 정규화한다")
    void koreanNameIsBothDisplayNameAndLoginAndNormalizesWhitespaceAndHangul() throws Exception {
        String decomposed = java.text.Normalizer.normalize("홍길동", java.text.Normalizer.Form.NFD);
        rosterService.registerEmployee("  " + decomposed + "  ", "staff-password", "GENERAL");
        assertThat(employeeRepository.findByName("홍길동")).isPresent();
        assertThat(rosterService.getEmployeeViews())
                .extracting(RosterService.EmployeeView::name)
                .containsExactly("홍길동");
        mockMvc.perform(
                        post("/login")
                                .with(csrf())
                                .param("name", " " + decomposed + " ")
                                .param("password", "staff-password"))
                .andExpect(redirectedUrl("/sharing-shop"));
        mockMvc.perform(
                        post("/login")
                                .with(csrf())
                                .param("username", "홍길동")
                                .param("password", "staff-password"))
                .andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"name\"")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "name=\"username\""))));
    }

    @Test
    @DisplayName("직원 이름 중복과 잘못된 한글 이름 등록을 거절한다")
    void duplicateNamesAreRejectedAndNonKoreanNewNamesAreNotAccepted() throws Exception {
        register("홍길동");
        assertThatThrownBy(
                        () -> rosterService.registerEmployee(" 홍길동 ", "staff-password", "GENERAL"))
                .hasMessageContaining("이미 등록된 이름");
        for (String invalid :
                java.util.Arrays.asList(
                        null, "", " ", "hong01", "홍 길동", "홍길동1", "홍길동!", "홍".repeat(31))) {
            assertThatThrownBy(
                            () ->
                                    rosterService.registerEmployee(
                                            invalid, "staff-password", "GENERAL"))
                    .hasMessageContaining("한글");
        }
        mockMvc.perform(
                        post("/admin/employees")
                                .with(testUser("admin", "ADMIN"))
                                .with(csrf())
                                .param("name", "홍길동")
                                .param("password", "staff-password")
                                .param("workGroupCode", "GENERAL"))
                .andExpect(redirectedUrl("/admin?section=employees"))
                .andExpect(flash().attribute("error", "이미 등록된 이름입니다."));
        assertThat(employeeRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("직원 비밀번호는 허용 문자와 8~20자 제한을 검사한다")
    void employeePasswordUsesAsciiCharactersAndLengthLimits() throws Exception {
        for (String password : java.util.Arrays.asList(null, "", "1234567", "a".repeat(21))) {
            assertThatThrownBy(() -> rosterService.registerEmployee("홍길동", password, "GENERAL"))
                    .hasMessage("비밀번호는 8~20자로 입력해주세요.");
        }
        for (String password :
                List.of(
                        "한글비밀번호입니다",
                        "Abcd123😀",
                        "Abcd 123!",
                        "Abcd123?",
                        "Abcd123\n",
                        "Abcd123\t")) {
            assertThatThrownBy(() -> rosterService.registerEmployee("홍길동", password, "GENERAL"))
                    .hasMessage("비밀번호는 영문, 숫자, 특수문자 !@#$%_-만 사용할 수 있습니다.");
        }
        rosterService.registerEmployee("홍길동", "Abcd123!", "GENERAL");
        rosterService.registerEmployee("김민수", "Aa0!@#$%_-Aa0!@#$%_-", "GENERAL");
        mockMvc.perform(
                        post("/login")
                                .with(csrf())
                                .param("name", "홍길동")
                                .param("password", "Abcd123!"))
                .andExpect(redirectedUrl("/sharing-shop"));
        mockMvc.perform(
                        post("/login")
                                .with(csrf())
                                .param("name", "김민수")
                                .param("password", "Aa0!@#$%_-Aa0!@#$%_-"))
                .andExpect(redirectedUrl("/sharing-shop"));
        mockMvc.perform(
                        get("/admin")
                                .param("section", "employees")
                                .with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("maxlength=\"20\"")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString("72바이트"))));
    }

    @Test
    @DisplayName("발달지원 직원을 등록하고 본인만 비밀번호를 변경한다")
    void onlyEmployeesCanChangeTheirOwnPassword() throws Exception {
        rosterService.registerEmployee("홍길동", "Initial1", "DEVELOPMENT_SUPPORT");
        register("김민수");
        String profile = "/employees/" + employeeId("홍길동");
        String passwordForm = profile + "/password";

        mockMvc.perform(get(profile).with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("발달지원")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(passwordForm)));
        mockMvc.perform(get(profile).with(testUser("김민수", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(passwordForm))));
        mockMvc.perform(post(passwordForm).with(csrf()).with(testUser("김민수", "STAFF"))
                        .param("currentPassword", "Initial1")
                        .param("newPassword", "Changed2!")
                        .param("confirmation", "Changed2!"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(passwordForm).with(csrf()).with(testUser("홍길동", "STAFF"))
                        .param("currentPassword", "Wrong123")
                        .param("newPassword", "Changed2!")
                        .param("confirmation", "Changed2!"))
                .andExpect(redirectedUrl(profile + "#change-password"))
                .andExpect(flash().attributeExists("error"));
        mockMvc.perform(post(passwordForm).with(csrf()).with(testUser("홍길동", "STAFF"))
                        .param("currentPassword", "Initial1")
                        .param("newPassword", "Changed2!")
                        .param("confirmation", "Mismatch2!"))
                .andExpect(redirectedUrl(profile + "#change-password"))
                .andExpect(flash().attributeExists("error"));
        mockMvc.perform(post(passwordForm).with(csrf()).with(testUser("홍길동", "STAFF"))
                        .param("currentPassword", "Initial1")
                        .param("newPassword", "Changed2!")
                        .param("confirmation", "Changed2!"))
                .andExpect(redirectedUrl(profile + "#change-password"))
                .andExpect(flash().attributeExists("success"));

        mockMvc.perform(post("/login").with(csrf()).param("name", "홍길동").param("password", "Initial1"))
                .andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(post("/login").with(csrf()).param("name", "홍길동").param("password", "Changed2!"))
                .andExpect(redirectedUrl("/sharing-shop"));
    }

    @Test
    @DisplayName("기존 비밀번호는 새 등록 규칙과 달라도 로그인할 수 있다")
    void existingPasswordsOutsideTheNewRegistrationRulesCanStillLogin() throws Exception {
        BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        employeeRepository.saveAndFlush(
                new Employee("홍길동", encoder.encode("기존한글비밀번호입니다"), TODAY, WorkGroupType.GENERAL));
        mockMvc.perform(
                        post("/login")
                                .with(csrf())
                                .param("name", "홍길동")
                                .param("password", "기존한글비밀번호입니다"))
                .andExpect(redirectedUrl("/sharing-shop"));
    }

    @Test
    @DisplayName("동시에 같은 이름을 등록해도 계정 하나만 생성된다")
    void concurrentSameNameRegistrationOnlyCreatesOneAccount() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> attempts =
                    executor.invokeAll(
                            List.<java.util.concurrent.Callable<Boolean>>of(
                                    () -> {
                                        try {
                                            register("홍길동");
                                            return true;
                                        } catch (BusinessException | DataIntegrityViolationException e) {
                                            return false;
                                        }
                                    },
                                    () -> {
                                        try {
                                            register("홍길동");
                                            return true;
                                        } catch (BusinessException | DataIntegrityViolationException e) {
                                            return false;
                                        }
                                    }));
            long successful = 0;
            for (Future<Boolean> attempt : attempts) if (attempt.get()) successful++;
            assertThat(successful).isEqualTo(1);
        }
        assertThat(employeeRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("직원 카드와 프로필에는 실제 가입 활동만 표시한다")
    void employeeCardsAndProfilesShowRegisteredActivities() throws Exception {
        rosterService.registerEmployee("홍길동", "staff-password", "THIRD_FLOOR");
        Long id = employeeId("홍길동");
        assertThat(rosterService.getEmployeeView(id).activityNames()).containsExactly(Activity.CAFETERIA_SUPPORT.getName());

        registerForActivity(Activity.SHARING_SHOP, "홍길동");
        mockMvc.perform(get("/admin").param("section", "employees").with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/admin/employees/" + id + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("담당 업무 목록"))));
        mockMvc.perform(get("/employees").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/employees/" + id + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<span>나눔가게 활동</span>")));
        mockMvc.perform(get("/employees/" + id).with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<span>나눔가게 활동</span>")));

        activityRegistrationService.deleteRegistration(Activity.SHARING_SHOP.getCode(), id);
        activityRegistrationService.deleteRegistration(Activity.CAFETERIA_SUPPORT.getCode(), id);
        assertThat(rosterService.getEmployeeViews().getFirst().activityNames()).isEmpty();
        assertThat(rosterService.getEmployeeView(id).activityNames()).isEmpty();
    }

    @Test
    @DisplayName("직군 변경은 관리자 직원 상세 화면에서만 가능하다")
    void workGroupIsEditableOnlyInsideAdminEmployeeManagement() throws Exception {
        register("김철수");
        register("홍길동");
        Long id = employeeId("김철수");
        for (org.springframework.test.web.servlet.request.RequestPostProcessor account : List.of(
                testUser("김철수", "STAFF"), testUser("홍길동", "STAFF"), testUser("admin", "ADMIN"))) {
            mockMvc.perform(get("/employees/" + id).param("management", "true").with(account))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("management", false))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"workGroupCode\""))));
        }
        for (String name : List.of("김철수", "홍길동")) {
            mockMvc.perform(get("/admin/employees/" + id).with(testUser(name, "STAFF")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/employees/" + id + "/work-group").with(testUser(name, "STAFF"))
                            .with(csrf()).param("workGroupCode", "RESTAURANT"))
                    .andExpect(status().isForbidden());
        }
        assertThat(rosterService.getEmployeeView(id).workGroupCode()).isEqualTo("GENERAL");
        mockMvc.perform(get("/admin/employees/" + id))
                .andExpect(redirectedUrl("http://localhost/login"));
        mockMvc.perform(get("/admin/employees/" + id).with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("management", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"workGroupCode\"")));
        mockMvc.perform(post("/admin/employees/" + id + "/work-group").with(testUser("admin", "ADMIN"))
                        .with(csrf()).param("workGroupCode", "THIRD_FLOOR"))
                .andExpect(redirectedUrl("/admin/employees/" + id));
        assertThat(rosterService.getEmployeeView(id).workGroupCode()).isEqualTo("THIRD_FLOOR");
        mockMvc.perform(post("/admin/employees/" + id + "/work-group").with(testUser("admin", "ADMIN"))
                        .with(csrf()).param("workGroupCode", "INVALID"))
                .andExpect(redirectedUrl("/admin/employees/" + id))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @DisplayName("프로필 사진은 본인·관리자만 등록하고 실제 이미지와 용량을 검사한다")
    void profilePhotosAreOwnerOrAdminOnlyAndMustBeRealImages() throws Exception {
        register("홍길동");
        registerRestaurant("강수진");
        Long id = employeeRepository.findByName("강수진").orElseThrow().getId();
        BufferedImage image =
                new java.awt.image.BufferedImage(
                        800, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "photo", "photo.png", "image/png", bytes.toByteArray());
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/employees/" + id + "/photo")
                                .file(file)
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(employeePhotoRepository.existsById(id)).isFalse();
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/employees/" + id + "/photo")
                                .file(file)
                                .with(testUser("강수진", "STAFF")))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/employees/" + id + "/photo")
                                .file(file)
                                .with(testUser("강수진", "STAFF"))
                                .with(csrf()))
                .andExpect(redirectedUrl("/employees/" + id));
        byte[] response =
                mockMvc.perform(get("/employees/" + id + "/photo").with(testUser("홍길동", "STAFF")))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType("image/jpeg"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        BufferedImage thumbnail =
                javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(response));
        assertThat(thumbnail.getWidth()).isEqualTo(512);
        assertThat(thumbnail.getHeight()).isEqualTo(256);
        assertThat(rosterService.getEmployeeView(id).hasPhoto()).isTrue();
        mockMvc.perform(get("/employees/" + id + "/photo")).andExpect(status().is3xxRedirection());
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/employees/" + id + "/photo")
                                .file(file)
                                .with(testUser("admin", "ADMIN"))
                                .with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/admin/employees/" + id + "/photo")
                                .file(file)
                                .with(testUser("강수진", "STAFF"))
                                .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/admin/employees/" + id + "/photo")
                                .file(file)
                                .with(testUser("admin", "ADMIN"))
                                .with(csrf()))
                .andExpect(redirectedUrl("/admin/employees/" + id));
        MockMultipartFile invalid =
                new org.springframework.mock.web.MockMultipartFile(
                        "photo",
                        "fake.png",
                        "image/png",
                        "<svg>not a photo</svg>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/employees/" + id + "/photo")
                                .file(invalid)
                                .with(testUser("강수진", "STAFF"))
                                .with(csrf()))
                .andExpect(redirectedUrl("/employees/" + id))
                .andExpect(flash().attribute("error", "JPG, PNG 또는 HEIC(HEIF) 사진을 선택해주세요."));
        MockMultipartFile oversized =
                new org.springframework.mock.web.MockMultipartFile(
                        "photo", "large.png", "image/png", new byte[2 * 1024 * 1024 + 1]);
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/employees/" + id + "/photo")
                                .file(oversized)
                                .with(testUser("강수진", "STAFF"))
                                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "사진은 2MB 이하의 JPG, PNG 또는 HEIC(HEIF) 파일로 선택해주세요."));
        assertThat(employeePhotoRepository.findById(id).orElseThrow().getImage())
                .isEqualTo(response);
    }

    @Test
    @DisplayName("HEIC 원본을 업로드하면 JPEG 썸네일로 저장한다")
    void heicProfilePhotosAreSavedAsJpegThumbnails() throws Exception {
        Assumptions.assumeTrue(Arrays.stream(System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator))
                .map(directory -> java.nio.file.Path.of(directory, "heif-thumbnailer"))
                .anyMatch(java.nio.file.Files::isExecutable), "heif-thumbnailer is required");
        register("홍길동");
        Long id = employeeId("홍길동");
        // A generated 640 × 320 two-color HEIC image.
        byte[] original = Base64.getMimeDecoder().decode("""
                AAAAJGZ0eXBoZWljAAAAAG1pZjFNaVBybWlhZk1pSEJoZWljAAABhG1ldGEAAAAAAAAAIWhkbHIAAAAAAAAAAHBpY3QAAAAAAAAA
                AAAAAAAAAAAAJGRpbmYAAAAcZHJlZgAAAAAAAAABAAAADHVybCAAAAABAAAADnBpdG0AAAAAAAEAAAAjaWluZgAAAAAAAQAAABVp
                bmZlAgAAAAABAABodmMxAAAAAORpcHJwAAAAw2lwY28AAAATY29scm5jbHgAAgACAAaAAAAADGNsbGkAywBAAAAAFGlzcGUAAAAA
                AAACgAAAAUAAAAAJaXJvdAAAAAAQcGl4aQAAAAADCAgIAAAAb2h2Y0MBA3AAAACwAAAAAAA/8AD8/fj4AAALA6AAAQAXQAEMAf//
                A3AAAAMAsAAAAwAAAwA/cCShAAEAIUIBAQNwAAADALAAAAMAAAMAP6AFAgFBYh7kWVTcCAgYAqIAAQAJRAHAYXLIQFMkAAAAGWlw
                bWEAAAAAAAAAAQABBoECAwWGhAAAAB5pbG9jAAAAAEQAAAEAAQAAAAEAAAG4AAAArQAAAAFtZGF0AAAAAAAAAL0AAACpKAGvijTp
                IGmFllEwRsV/+4s//v0nIdSv4z+2fNKPZJjXgNUtf/vWO+5KT/7mMe8YgAAC/j5txfzjiovyvzT4YcAAAAMAKaAisWIj7WKd/anG
                gAAAAwBcQAqvXqLYRQAIVNAAABjwAuHwigAABW2wAACzgAPSwAAAAwFZIAABmQAAAwAAAwBO0gACCgAAAwAABnOgALKAAAADAABr
                WAAlYAAAAwADLAADPg==
                """);
        MockMultipartFile file = new MockMultipartFile("photo", "profile.heic", "application/octet-stream", original);
        mockMvc.perform(multipart("/employees/" + id + "/photo").file(file).with(testUser("홍길동", "STAFF")).with(csrf()))
                .andExpect(redirectedUrl("/employees/" + id))
                .andExpect(flash().attribute("success", "프로필 사진을 저장했습니다."));

        byte[] result = mockMvc.perform(get("/employees/" + id + "/photo").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        BufferedImage thumbnail = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(result));
        assertThat(thumbnail.getWidth()).isEqualTo(512);
        assertThat(thumbnail.getHeight()).isEqualTo(256);

        MockMultipartFile corrupt = new MockMultipartFile("photo", "broken.heic", "image/heic", Arrays.copyOf(original, 36));
        mockMvc.perform(multipart("/employees/" + id + "/photo").file(corrupt).with(testUser("홍길동", "STAFF")).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        assertThat(employeePhotoRepository.findById(id).orElseThrow().getImage()).isEqualTo(result);
    }

    @Test
    @DisplayName("직군은 정해진 목록에서 하나만 선택하고 비밀번호 표시를 사용할 수 있다")
    void groupsAreSingleChoiceAndPasswordVisibilityIsAvailable() throws Exception {
        mockMvc.perform(get("/admin").param("section", "employees").with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"RESTAURANT\">식당</option>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"GENERAL\">운영지원</option>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"FACILITY_SUPPORT\">운영지원(기계실)</option>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"THIRD_FLOOR\">3층</option>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<option value=\"DEVELOPMENT_SUPPORT\">발달지원</option>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-password-toggle")));
        mockMvc.perform(post("/admin/employees").with(testUser("admin", "ADMIN")).with(csrf())
                        .param("name", "홍길동").param("password", "Staff!2026")
                        .param("workGroupCode", "FACILITY_SUPPORT"))
                .andExpect(redirectedUrl("/admin?section=employees"));
        assertThat(rosterService.getEmployeeView(employeeId("홍길동")).workGroupName())
                .isEqualTo("운영지원(기계실)");
        assertThatThrownBy(() -> rosterService.registerEmployee("김민수", "Staff!2026", "GENERAL,RESTAURANT"))
                .hasMessageContaining("직군");
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("비밀번호 보기")));
        mockMvc.perform(get("/forms.js")).andExpect(status().isOk());
    }
}

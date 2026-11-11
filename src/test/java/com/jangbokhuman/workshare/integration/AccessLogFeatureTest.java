package com.jangbokhuman.workshare.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@DisplayName("직원별 요청 기록")
class AccessLogFeatureTest extends BusinessFeatureTestSupport {
    private final Logger accessLogger = (Logger) LoggerFactory.getLogger("workshare.access");
    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

    @BeforeEach
    void captureAccessLog() {
        lines.start();
        accessLogger.addAppender(lines);
    }

    @AfterEach
    void releaseAccessLog() {
        accessLogger.detachAppender(lines);
    }

    @Test
    @DisplayName("로그인 결과와 요청마다 누가 어떤 주소를 불렀는지 한 줄로 남기고, 정적 파일과 상태 확인은 남기지 않는다")
    void writesOneLinePerRequestWithTheAccount() throws Exception {
        register("홍길동");
        Long id = employeeId("홍길동");

        mockMvc.perform(post("/login").with(csrf()).param("name", "홍길동").param("password", "wrong-password"));
        mockMvc.perform(post("/login").with(csrf()).param("name", "홍길동").param("password", "staff-password"));
        mockMvc.perform(get("/leave?date=" + TODAY).with(testUser("홍길동", "STAFF")));
        mockMvc.perform(get("/admin/leave").with(testUser("admin", "ADMIN")));
        mockMvc.perform(get("/actuator/health"));
        mockMvc.perform(get("/style.css"));

        assertThat(lines.list).extracting(ILoggingEvent::getFormattedMessage)
                .hasSize(4)
                .anySatisfy(line -> assertThat(line).isEqualTo("로그인 실패 · 입력한 이름 홍길동"))
                .anySatisfy(line -> assertThat(line).isEqualTo("직원 ID " + id + " 로그인 성공"))
                .anySatisfy(line -> assertThat(line).matches("직원 ID " + id + " GET /leave\\?date=" + TODAY + " 200 \\d+ms"))
                .anySatisfy(line -> assertThat(line).matches("관리자 GET /admin/leave 200 \\d+ms"));
    }
}

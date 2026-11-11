package com.jangbokhuman.workshare.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jangbokhuman.workshare.employee.ProfileMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

@DisplayName("프로필 한마디")
class ProfileMessageFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("직원은 동료 프로필에 이름이나 익명으로 한마디를 남기고, 익명이어도 작성자는 저장한다")
    void staffLeaveNamedOrAnonymousMessagesAndTheAuthorIsAlwaysStored() throws Exception {
        register("홍길동");
        register("김민수");
        register("이서연");
        Long recipient = employeeId("홍길동");

        write(recipient, "김민수", "어제 대신 가 줘서 고마워요", false)
                .andExpect(redirectedUrl("/employees/" + recipient))
                .andExpect(flash().attribute("messageNotice", "한마디를 남겼어요."));
        testClock.set(TODAY.atTime(9, 0));
        write(recipient, "이서연", "늘 응원해요\r\n화이팅", true);

        String page = profile(recipient, "홍길동", "STAFF");
        assertThat(page)
                .contains("<strong>김민수</strong>", "어제 대신 가 줘서 고마워요", "<strong>익명</strong>", "늘 응원해요\n화이팅")
                .doesNotContain("<strong>이서연</strong>", "name=\"content\"");
        assertThat(page.indexOf("늘 응원해요")).isLessThan(page.indexOf("어제 대신 가 줘서 고마워요"));
        assertThat(profileMessageRepository.findAll())
                .extracting(ProfileMessage::getAuthorId)
                .containsExactlyInAnyOrder(employeeId("김민수"), employeeId("이서연"));

        String adminPage = mockMvc.perform(get("/admin/employees/{id}", recipient).with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(adminPage).contains("<strong>익명</strong>").doesNotContain("<strong>이서연</strong>", "name=\"content\"");
    }

    @Test
    @DisplayName("작성자만 고칠 수 있고, 지우기는 작성자와 관리자만 할 수 있다")
    void onlyTheAuthorEditsAndOnlyTheAuthorOrAdminDeletes() throws Exception {
        register("홍길동");
        register("김민수");
        register("이서연");
        Long recipient = employeeId("홍길동");
        write(recipient, "김민수", "처음 쓴 말", false);
        Long messageId = profileMessageRepository.findAll().getFirst().getId();

        mockMvc.perform(put("/employees/{id}/messages/{messageId}", recipient, messageId)
                        .with(testUser("이서연", "STAFF")).with(csrf())
                        .param("content", "남의 글 고치기"))
                .andExpect(flash().attribute("messageError", "내가 남긴 한마디만 고칠 수 있어요."));
        mockMvc.perform(delete("/employees/{id}/messages/{messageId}", recipient, messageId)
                        .with(testUser("이서연", "STAFF")).with(csrf()))
                .andExpect(flash().attribute("messageError", "내가 남긴 한마디만 지울 수 있어요."));
        mockMvc.perform(delete("/employees/{id}/messages/{messageId}", recipient, messageId)
                        .with(testUser("홍길동", "STAFF")).with(csrf()))
                .andExpect(flash().attribute("messageError", "내가 남긴 한마디만 지울 수 있어요."));

        testClock.set(TODAY.atTime(9, 0));
        mockMvc.perform(put("/employees/{id}/messages/{messageId}", recipient, messageId)
                        .with(testUser("김민수", "STAFF")).with(csrf())
                        .param("content", "고친 말")
                        .param("anonymous", "true"))
                .andExpect(flash().attribute("messageNotice", "한마디를 고쳤어요."));
        ProfileMessage changed = profileMessageRepository.findById(messageId).orElseThrow();
        assertThat(changed.getContent()).isEqualTo("고친 말");
        assertThat(changed.isAnonymous()).isTrue();
        assertThat(changed.isEdited()).isTrue();
        assertThat(profile(recipient, "김민수", "STAFF")).contains("고친 말", " · 수정됨", " · 내가 쓴 글", "data-message-edit");
        assertThat(profile(recipient, "이서연", "STAFF")).contains("고친 말").doesNotContain("data-message-edit", "data-message-delete");

        mockMvc.perform(delete("/employees/{id}/messages/{messageId}", recipient, messageId)
                        .with(testUser("admin", "ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/employees/{id}/messages/{messageId}", recipient, messageId)
                        .with(testUser("김민수", "STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/admin/employees/{id}/messages/{messageId}", recipient, messageId)
                        .with(testUser("admin", "ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/employees/" + recipient))
                .andExpect(flash().attribute("messageNotice", "한마디를 지웠어요."));
        assertThat(profileMessageRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("내 프로필, 빈 내용, 500자를 넘는 내용은 남길 수 없고 관리자는 쓰지 못한다")
    void rejectsOwnProfileBlankAndTooLongMessages() throws Exception {
        register("홍길동");
        register("김민수");
        Long recipient = employeeId("홍길동");

        write(recipient, "홍길동", "셀프 칭찬", false)
                .andExpect(flash().attribute("messageError", "내 프로필에는 한마디를 남길 수 없어요."));
        write(recipient, "김민수", "  \r\n ", false)
                .andExpect(flash().attribute("messageError", "한마디를 입력해주세요."));
        write(recipient, "김민수", "가".repeat(501), false)
                .andExpect(flash().attribute("messageError", "한마디는 500자까지 쓸 수 있어요."));
        write(recipient, "김민수", "가\r\n".repeat(250).strip(), false)
                .andExpect(flash().attribute("messageNotice", "한마디를 남겼어요."));
        mockMvc.perform(post("/employees/{id}/messages", recipient)
                        .with(testUser("admin", "ADMIN")).with(csrf())
                        .param("content", "관리자 한마디"))
                .andExpect(status().isForbidden());
        assertThat(profileMessageRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("목록만 따로 불러와 주기적으로 새로 고칠 수 있고, 내용은 HTML로 해석하지 않는다")
    void servesTheListAloneForPollingAndEscapesContent() throws Exception {
        register("홍길동");
        register("김민수");
        Long recipient = employeeId("홍길동");
        write(recipient, "김민수", "<script>alert(1)</script>", false);

        String list = mockMvc.perform(get("/employees/{id}/messages", recipient).with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list.strip()).startsWith("<div class=\"message-list\" data-message-list>");
        assertThat(list).contains("&lt;script&gt;alert(1)&lt;/script&gt;", "<strong>김민수</strong>")
                .doesNotContain("<script>alert", "<html");
        mockMvc.perform(get("/employees/{id}/messages", recipient)).andExpect(status().is3xxRedirection());
    }

    private org.springframework.test.web.servlet.ResultActions write(Long recipient, String author, String content, boolean anonymous)
            throws Exception {
        testClock.set(LocalDateTime.ofInstant(testClock.instant(), testClock.getZone()).plusSeconds(1));
        return mockMvc.perform(post("/employees/{id}/messages", recipient)
                .with(testUser(author, "STAFF")).with(csrf())
                .param("content", content)
                .param("anonymous", String.valueOf(anonymous)));
    }

    private String profile(Long id, String viewer, String role) throws Exception {
        return mockMvc.perform(get("/employees/{id}", id).with(testUser(viewer, role)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}

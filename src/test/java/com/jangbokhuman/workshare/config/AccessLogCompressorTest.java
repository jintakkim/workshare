package com.jangbokhuman.workshare.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@DisplayName("요청 기록 압축")
class AccessLogCompressorTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock NOV_17 = Clock.fixed(Instant.parse("2026-11-16T15:10:00Z"), SEOUL);

    @TempDir Path logs;

    @Test
    @DisplayName("10일보다 오래된 날은 .gz로 압축해 남기고, 최근 10일과 다른 파일은 그대로 둔다")
    void compressesDaysOlderThanTheSettingAndKeepsThem() throws IOException {
        write("access.2026-11-06.log", "직원 ID 11 GET /leave 200 5ms\n");
        write("access.2026-11-01.log", "직원 ID 3 PUT /leave/2026-11-01 302 9ms\n");
        write("access.2026-11-07.log", "recent");
        write("access.log", "today");
        write("error.2026-10-01.log", "error");

        new AccessLogCompressor(logs, 10, NOV_17).compressOldLogs();

        assertThat(logs.resolve("access.2026-11-06.log")).doesNotExist();
        assertThat(logs.resolve("access.2026-11-01.log")).doesNotExist();
        assertThat(readGzip("access.2026-11-06.log.gz")).isEqualTo("직원 ID 11 GET /leave 200 5ms\n");
        assertThat(readGzip("access.2026-11-01.log.gz")).isEqualTo("직원 ID 3 PUT /leave/2026-11-01 302 9ms\n");
        assertThat(logs.resolve("access.2026-11-07.log")).hasContent("recent");
        assertThat(logs.resolve("access.log")).hasContent("today");
        assertThat(logs.resolve("error.2026-10-01.log")).hasContent("error");
        try (var files = Files.list(logs)) {
            assertThat(files.map(file -> file.getFileName().toString())).noneMatch(name -> name.endsWith(".part"));
        }
    }

    @Test
    @DisplayName("이미 압축한 날은 다시 압축하지 않고, 폴더가 없으면 아무것도 하지 않는다")
    void skipsAlreadyCompressedDaysAndMissingFolders() throws IOException {
        write("access.2026-11-01.log", "leftover");
        Files.write(logs.resolve("access.2026-11-01.log.gz"), gzip("first"));

        new AccessLogCompressor(logs, 10, NOV_17).compressOldLogs();
        new AccessLogCompressor(logs.resolve("missing"), 10, NOV_17).compressOldLogs();

        assertThat(logs.resolve("access.2026-11-01.log")).doesNotExist();
        assertThat(readGzip("access.2026-11-01.log.gz")).isEqualTo("first");
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(logs.resolve(name), content);
    }

    private String readGzip(String name) throws IOException {
        try (InputStream input = new GZIPInputStream(Files.newInputStream(logs.resolve(name)))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private byte[] gzip(String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream output = new GZIPOutputStream(bytes)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}

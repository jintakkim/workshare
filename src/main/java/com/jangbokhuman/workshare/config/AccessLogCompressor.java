package com.jangbokhuman.workshare.config;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

// Logback rolls access.log into one file per day but can only delete old days, not compress them later.
// Recent days stay plain for grep; older days are gzipped and kept.
@Slf4j
@Component
public class AccessLogCompressor {
    private static final Pattern DAILY_FILE = Pattern.compile("access\\.(\\d{4}-\\d{2}-\\d{2})\\.log");

    private final Path logDirectory;
    private final int plainDays;
    private final Clock clock;

    public AccessLogCompressor(
            @Value("${app.error-log-directory}") Path logDirectory,
            @Value("${app.access-log-days}") int plainDays,
            Clock clock
    ) {
        this.logDirectory = logDirectory;
        this.plainDays = plainDays;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 10 0 * * *", zone = "${app.zone}")
    public void compressOldLogs() {
        if (!Files.isDirectory(logDirectory)) {
            return;
        }
        LocalDate oldestPlainDay = LocalDate.now(clock).minusDays(plainDays);
        for (Path file : findDailyFilesBefore(oldestPlainDay)) {
            try {
                compress(file);
            } catch (IOException error) {
                log.error("요청 기록 파일을 압축하지 못했습니다: {}", file, error);
            }
        }
    }

    private List<Path> findDailyFilesBefore(LocalDate day) {
        try (Stream<Path> files = Files.list(logDirectory)) {
            return files.filter(file -> {
                Matcher matcher = DAILY_FILE.matcher(file.getFileName().toString());
                return matcher.matches() && LocalDate.parse(matcher.group(1)).isBefore(day);
            }).toList();
        } catch (IOException error) {
            log.error("요청 기록 폴더를 읽지 못했습니다: {}", logDirectory, error);
            return List.of();
        }
    }

    private void compress(Path file) throws IOException {
        Path compressed = file.resolveSibling(file.getFileName() + ".gz");
        if (!Files.exists(compressed)) {
            Path partial = file.resolveSibling(file.getFileName() + ".gz.part");
            try (InputStream input = Files.newInputStream(file);
                 OutputStream output = new GZIPOutputStream(Files.newOutputStream(partial))) {
                input.transferTo(output);
            }
            Files.move(partial, compressed, StandardCopyOption.ATOMIC_MOVE);
        }
        Files.delete(file);
    }
}

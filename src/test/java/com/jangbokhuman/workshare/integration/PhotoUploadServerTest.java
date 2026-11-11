package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.employee.EmployeePhotoRepository;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.employee.RosterService;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

// Upload size limits are enforced by Tomcat, so these run against a real server instead of MockMvc.
@DisplayName("실제 서버에서 휴대폰 사진 업로드")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:photo_upload;DB_CLOSE_DELAY=-1",
            "app.admin-password=test-admin-password-2026",
            "app.remember-me-key=test-remember-me-key-0123456789abcdef"
        })
@ActiveProfiles("test")
class PhotoUploadServerTest {
    private static final String STAFF_PASSWORD = "staff-password";
    private static final String NAME_SUFFIXES = "가나다라마바사아자차";
    private static final AtomicInteger NEXT_NAME = new AtomicInteger();
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*name=\"_csrf\"");

    @LocalServerPort int port;
    @Autowired RosterService rosterService;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired EmployeePhotoRepository employeePhotoRepository;

    private HttpClient client;
    private Long employeeId;

    @BeforeEach
    void loginAsNewEmployee() throws Exception {
        String name = "직원" + NAME_SUFFIXES.charAt(NEXT_NAME.getAndIncrement());
        rosterService.registerEmployee(name, STAFF_PASSWORD, "GENERAL");
        employeeId = employeeRepository.findByName(name).orElseThrow().getId();
        client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        String loginForm = "name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&password=" + STAFF_PASSWORD
                + "&_csrf=" + findCsrf(get("/login").body());
        client.send(
                HttpRequest.newBuilder(uri("/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(loginForm))
                        .build(),
                HttpResponse.BodyHandlers.discarding()
        );
    }

    @Test
    @DisplayName("2MB가 넘는 휴대폰 사진도 저장하고 프로필로 돌아간다")
    void savesPhotosLargerThanTwoMegabytes() throws Exception {
        byte[] photo = createNoisePng(1200, 1000);
        assertThat(photo.length).isGreaterThan(3 * 1024 * 1024);

        HttpResponse<String> upload = uploadPhoto(photo, "photo.png", "image/png");

        assertThat(upload.headers().firstValue("Location")).hasValue("/employees/" + employeeId);
        assertThat(employeePhotoRepository.existsById(employeeId)).isTrue();
    }

    @Test
    @DisplayName("업로드 한도를 넘으면 일정 화면이 아니라 프로필로 돌아가 이유를 보여준다")
    void returnsToTheProfileWhenThePhotoIsTooLarge() throws Exception {
        HttpResponse<String> upload = uploadPhoto(new byte[17 * 1024 * 1024], "huge.jpg", "image/jpeg");

        assertThat(upload.headers().firstValue("Location")).hasValue("/employees/" + employeeId);
        assertThat(get("/employees/" + employeeId).body()).contains("사진은 15MB 이하");
        assertThat(employeePhotoRepository.existsById(employeeId)).isFalse();
    }

    @Test
    @DisplayName("휴대폰이 EXIF로 돌려 놓은 JPEG는 바로 선 방향으로 저장한다")
    void appliesExifOrientation() throws Exception {
        byte[] sideways = addExifOrientation(createJpeg(400, 200), 6);

        uploadPhoto(sideways, "sideways.jpg", "image/jpeg");

        BufferedImage saved = ImageIO.read(new ByteArrayInputStream(get("/employees/" + employeeId + "/photo").bodyAsBytes()));
        assertThat(saved.getWidth()).isEqualTo(200);
        assertThat(saved.getHeight()).isEqualTo(400);
    }

    @Test
    @DisplayName("저장 뒤 돌아가는 주소는 상대 경로라 https 화면에서 한 번만 저장된다")
    void redirectsWithARelativeLocationSoHttpsPagesDoNotPostTwice() throws Exception {
        String recipientName = "직원" + NAME_SUFFIXES.charAt(NEXT_NAME.getAndIncrement());
        rosterService.registerEmployee(recipientName, STAFF_PASSWORD, "GENERAL");
        Long recipientId = employeeRepository.findByName(recipientName).orElseThrow().getId();
        String form = "content=" + URLEncoder.encode("고마워요", StandardCharsets.UTF_8)
                + "&_csrf=" + findCsrf(get("/employees/" + recipientId).body());

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(uri("/employees/" + recipientId + "/messages"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).hasValue("/employees/" + recipientId);
    }

    private HttpResponse<String> uploadPhoto(byte[] photo, String fileName, String contentType) throws Exception {
        String token = findCsrf(get("/employees/" + employeeId).body());
        String boundary = "----workshare-photo";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeField(body, boundary, "_csrf", token);
        writeField(body, boundary, "_method", "put");
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"" + fileName
                + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(photo);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.send(
                HttpRequest.newBuilder(uri("/employees/" + employeeId + "/photo"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private void writeField(ByteArrayOutputStream body, String boundary, String name, String value) throws IOException {
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private Page get(String path) throws Exception {
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofByteArray());
        return new Page(response.body());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String findCsrf(String html) {
        Matcher matcher = CSRF.matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("CSRF 토큰이 없습니다.");
        }
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private byte[] createNoisePng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] createJpeg(int width, int height) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", output);
        return output.toByteArray();
    }

    // Inserts an APP1 Exif segment holding only the Orientation tag right after the JPEG start marker.
    private byte[] addExifOrientation(byte[] jpeg, int orientation) {
        byte[] exif = {
            (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
            'E', 'x', 'i', 'f', 0x00, 0x00,
            'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
            0x00, 0x01,
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        };
        byte[] result = new byte[jpeg.length + exif.length];
        System.arraycopy(jpeg, 0, result, 0, 2);
        System.arraycopy(exif, 0, result, 2, exif.length);
        System.arraycopy(jpeg, 2, result, 2 + exif.length, jpeg.length - 2);
        return result;
    }

    private record Page(byte[] bodyAsBytes) {
        String body() {
            return new String(bodyAsBytes, StandardCharsets.UTF_8);
        }
    }
}

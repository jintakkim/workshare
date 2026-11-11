package com.jangbokhuman.workshare.employee;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;

import java.awt.image.BufferedImage;

@DisplayName("EXIF 방향에 따른 사진 회전·뒤집기")
class JpegOrientationTest {
    private static final int MARK = 0xFF0000;

    @ParameterizedTest(name = "방향 {0}: 왼쪽 위 점이 ({1}, {2})로 간다")
    @CsvSource({
            "1, 0, 0",
            "2, 2, 0",
            "3, 2, 1",
            "4, 0, 1",
            "5, 0, 0",
            "6, 1, 0",
            "7, 1, 2",
            "8, 0, 2"
    })
    void movesTheTopLeftCornerWhereThePhoneShowsIt(int orientation, int expectedX, int expectedY) {
        BufferedImage stored = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        stored.setRGB(0, 0, MARK);

        BufferedImage shown = JpegOrientation.apply(stored, orientation);

        boolean swapsSides = orientation >= 5;
        assertThat(shown.getWidth()).isEqualTo(swapsSides ? 2 : 3);
        assertThat(shown.getHeight()).isEqualTo(swapsSides ? 3 : 2);
        assertThat(shown.getRGB(expectedX, expectedY) & 0xFFFFFF).isEqualTo(MARK);
    }
}

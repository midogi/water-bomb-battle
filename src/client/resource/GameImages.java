package client.resource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GameImages {
    /** 이미지를 읽는 기능은 load()로 바로 사용하므로 이 클래스의 인스턴스를 따로 만들지 않는다. */
    private GameImages() { }

    /** 먼저 클래스 경로의 /res/images를 찾고, 없으면 작업 폴더의 res/images에서 읽는다. 실패하면 이미지 이름과 원인을 함께 전달한다. */
    public static BufferedImage load(String name) {
        InputStream resource = GameImages.class.getResourceAsStream("/res/images/" + name);
        try (InputStream input = resource == null
                ? Files.newInputStream(Path.of("res", "images", name)) : resource) {
            BufferedImage image = ImageIO.read(input);
            // 지원하지 않는 형식이면 ImageIO가 null을 반환하므로 파일을 읽는 시점에 오류로 처리한다.
            if (image == null) {
                throw new IOException("알 수 없는 이미지 형식");
            }
            return image;
        } catch (IOException error) {
            throw new IllegalStateException("게임 이미지를 읽을 수 없습니다: res/images/" + name, error);
        }
    }
}

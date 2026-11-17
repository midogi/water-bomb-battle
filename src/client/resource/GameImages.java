package client.resource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GameImages {
    private GameImages() { }

    // 프로젝트 루트에서 실행할 때와 res를 클래스 경로에 포함했을 때 모두 읽을 수 있다.
    public static BufferedImage load(String name) {
        InputStream resource = GameImages.class.getResourceAsStream("/res/images/" + name);
        try (InputStream input = resource == null
                ? Files.newInputStream(Path.of("res", "images", name)) : resource) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("알 수 없는 이미지 형식");
            }
            return image;
        } catch (IOException error) {
            throw new IllegalStateException("게임 이미지를 읽을 수 없습니다: res/images/" + name, error);
        }
    }
}

package com.kkodong.server.global.storage;

import com.kkodong.server.global.config.MediaProperties;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.UUID;

/**
 * 유치원 미디어(사진·영상) 저장(PN-15).
 *
 * <p><b>{@link ImageStorageService}와 따로 두는 이유</b>: 반려견 프로필 사진은 이미지 전용이고
 * 상한이 고정이지만, 유치원 미디어는 영상을 포함하고 용량·해상도 정책이 설정으로 바뀐다
 * (UD-C 미결). 기존 서비스에 분기를 더하면 두 용도의 규칙이 한 함수에 섞인다.
 *
 * <p><b>서버는 리사이즈하지 않는다.</b> 상한을 넘으면 거부한다. 이 코드베이스는 이미
 * 누끼 생성을 온디바이스로 처리해 "서버 이미지 처리 비용 0"을 원칙으로 세웠고
 * (FR-OB04-01), 리사이즈도 같은 이유로 클라이언트 몫이다. 서버가 조용히 줄여 주면
 * 클라이언트는 원본을 계속 올리고, 그 트래픽과 CPU 비용은 사라지지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MediaStorageService {

    private final S3Client r2Client;
    private final MediaProperties mediaProperties;

    @Value("${r2.bucket}")
    private String bucket;

    @Value("${r2.public-url}")
    private String publicUrl;

    /**
     * 검증 후 업로드하고 저장된 결과를 돌려준다.
     *
     * @throws BusinessException 타입·용량·해상도 위반 시
     */
    public StoredMedia upload(MultipartFile file, String keyPrefix) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_MEDIA_FILE);
        }
        String contentType = file.getContentType();
        String extension = mediaProperties.extensionOf(contentType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.INVALID_MEDIA_FILE);
        }

        boolean image = mediaProperties.isImage(contentType);
        long limit = image ? mediaProperties.maxImageBytes() : mediaProperties.maxVideoBytes();
        if (file.getSize() > limit) {
            throw new BusinessException(ErrorCode.MEDIA_TOO_LARGE);
        }

        // 해상도는 이미지만 본다. 영상 메타데이터를 읽으려면 별도 라이브러리가 필요한데,
        // 용량 상한으로 이미 대부분 걸러지므로 지금은 들이지 않는다.
        Dimension dimension = image ? readDimension(file) : null;
        if (dimension != null
                && (long) dimension.width() * dimension.height() > mediaProperties.maxImagePixels()) {
            throw new BusinessException(ErrorCode.MEDIA_RESOLUTION_TOO_HIGH);
        }

        String key = "%s/%s.%s".formatted(keyPrefix, UUID.randomUUID(), extension);
        try {
            r2Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception e) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_FAILED);
        }

        return new StoredMedia(
                publicUrl + "/" + key,
                image,
                file.getSize(),
                dimension == null ? null : dimension.width(),
                dimension == null ? null : dimension.height());
    }

    /**
     * 이미지 해상도를 헤더만 읽어 얻는다. 전체를 디코딩하면 큰 사진 하나에 수백 MB가 잡힌다.
     *
     * <p>읽기에 실패해도 업로드를 막지 않는다 — 해상도는 상한 검사와 용량 추이 측정용이고,
     * 못 읽었다고 사진을 잃는 편이 훨씬 나쁘다. 그 경우 크기는 null로 남는다.
     */
    private Dimension readDimension(MultipartFile file) {
        try (InputStream in = file.getInputStream();
             ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            if (stream == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private record Dimension(int width, int height) {}

    /**
     * 저장 결과.
     *
     * @param url        공개 URL
     * @param image      이미지면 true, 영상이면 false
     * @param sizeBytes  실제 저장된 용량. ⚠️ PC-23 — 스토리지 증가 속도를 재는 값이라 반드시 기록한다
     * @param width      이미지 가로. 읽지 못했거나 영상이면 null
     * @param height     이미지 세로
     */
    public record StoredMedia(String url, boolean image, long sizeBytes, Integer width, Integer height) {}
}

package com.kkodong.server.domain.safety.dto;

import com.kkodong.server.domain.safety.domain.Block;
import com.kkodong.server.domain.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

public class BlockResponse {

    /**
     * 차단 시각(createdAt)은 일부러 넣지 않는다. Block.createdAt은 DB DEFAULT now()가
     * 채우는 값이라 INSERT 직후 엔티티에는 null이고, 기존 차단을 조회한 경우에만 값이 있어
     * 같은 API가 상황에 따라 null/값을 오간다. 차단 시각이 필요한 화면은 차단 목록
     * (GET /api/v1/blocks)뿐이고 거기선 항상 DB에서 읽으므로 안전하다.
     */
    public record detailInfo(
            @Schema(description = "차단된 유저 ID") UUID blockedId
    ) {
        public static detailInfo from(Block block) {
            return new detailInfo(block.getBlockedId());
        }
    }

    /**
     * 차단 목록 항목. UUID만 내려주면 클라이언트가 "차단 해제" 리스트를 그릴 수 없어
     * 상대 유저의 표시 정보를 함께 담는다.
     */
    public record listItem(
            @Schema(description = "차단된 유저 ID") UUID blockedId,
            @Schema(description = "닉네임", example = "초코아빠") String displayName,
            @Schema(description = "프로필 이미지 URL") String profileImageUrl,
            @Schema(description = "차단한 시각") OffsetDateTime createdAt
    ) {
        /**
         * @param user blocks.blocked_id는 users에 ON DELETE CASCADE로 걸려 있어 정상적으로는
         *             null이 될 수 없지만, 방어적으로 null을 허용한다.
         */
        public static listItem of(Block block, User user) {
            return new listItem(
                    block.getBlockedId(),
                    user == null ? null : user.getDisplayName(),
                    user == null ? null : user.getProfileImageUrl(),
                    block.getCreatedAt()
            );
        }
    }
}

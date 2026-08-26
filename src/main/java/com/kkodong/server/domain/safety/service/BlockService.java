package com.kkodong.server.domain.safety.service;

import com.kkodong.server.domain.safety.domain.Block;
import com.kkodong.server.domain.safety.dto.BlockRequest;
import com.kkodong.server.domain.safety.dto.BlockResponse;
import com.kkodong.server.domain.safety.repository.BlockRepository;
import com.kkodong.server.domain.user.domain.User;
import com.kkodong.server.domain.user.repository.UserRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    @Transactional
    public BlockResponse.detailInfo block(UUID blockerId, BlockRequest.create request) {

        // 차단하려는 유저가 자기 자신인 경우
        if (request.blockedId().equals(blockerId)) { throw new BusinessException(ErrorCode.SELF_BLOCK_NOT_ALLOWED); }

        // 차단하려는 유저가 없는 경우 (엔티티를 쓰지 않으므로 존재 여부만 확인)
        if (!userRepository.existsById(request.blockedId())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        blockRepository.insertIfAbsent(blockerId, request.blockedId());

        // 방금 넣었든 원래 있었든 결과는 항상 DB에서 읽는다 — 그래야 created_at 같은
        // DB DEFAULT 값이 채워진 상태로 나온다.
        Block block = blockRepository.findByBlockerIdAndBlockedId(blockerId, request.blockedId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        return BlockResponse.detailInfo.from(block);
    }


    /**
     * 차단 해제. 차단돼 있지 않았어도 예외를 던지지 않는다 —
     * 호출자가 원한 최종 상태("차단돼 있지 않음")는 어느 쪽이든 달성되기 때문이다(멱등).
     * 차단(block)이 멱등인 것과 짝을 맞춘다.
     */
    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    /**
     * 차단 목록. 상대 유저를 건별로 조회하면 N+1이 되므로 id를 모아 한 번에 읽는다
     * (쿼리 2회 고정). 차단 목록은 보통 수십 건 이하라 페이징은 두지 않는다.
     */
    public List<BlockResponse.listItem> getBlocks(UUID blockerId) {
        List<Block> blocks = blockRepository.findByBlockerIdOrderByCreatedAtDesc(blockerId);
        if (blocks.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> users = userRepository
                .findAllById(blocks.stream().map(Block::getBlockedId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return blocks.stream()
                .map(block -> BlockResponse.listItem.of(block, users.get(block.getBlockedId())))
                .toList();
    }
}

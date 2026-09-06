package com.kkodong.server.domain.friend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 6개 신호 점수 → 합산 · 동점 셔플
 */
@Service
@RequiredArgsConstructor
public class RecommendationScorer {
}

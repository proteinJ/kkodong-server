package com.kkodong.server.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 받침 판정은 순수 계산이라 여기서 전부 덮을 수 있다.
 *
 * <p>추천 응답으로는 검증하기 어렵다 — 견종 절이 이유 문장에 실리려면 시간대·성격이
 * 모두 비어 우선순위 상위 2개에 들어가야 하는데, 그런 조합이 흔하지 않다.
 */
class KoreanParticleTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("받침이 있으면 이에요, 없으면 예요")
    @CsvSource({
            "푸들,     푸들이에요",       // ㄹ 받침
            "파피용,   파피용이에요",     // ㅇ 받침 — '로/으로' 였다면 여기서 갈린다
            "비글,     비글이에요",
            "포메라니안, 포메라니안이에요",
            "말티즈,   말티즈예요",       // 받침 없음
            "진돗개,   진돗개예요",
            "시츄,     시츄예요",
            "웰시코기, 웰시코기예요"
    })
    void iyeyo(String word, String expected) {
        assertThat(KoreanParticle.iyeyo(word)).isEqualTo(expected);
    }

    @Test
    @DisplayName("한글 음절이 아니면 받침이 없는 것으로 본다")
    void nonHangul() {
        // 받침 개념이 없는 글자에 "있다"고 답하는 것보다 낫다. 현재 견종 목록은 전부
        // 한글이라 실제로는 타지 않는 경로다.
        assertThat(KoreanParticle.hasFinalConsonant("poodle")).isFalse();
        assertThat(KoreanParticle.hasFinalConsonant("3")).isFalse();
    }

    @Test
    @DisplayName("붙일 말이 없으면 그대로 돌려준다")
    void blank() {
        // "null예요" 가 사용자에게 나가는 것을 막는다.
        assertThat(KoreanParticle.iyeyo(null)).isNull();
        assertThat(KoreanParticle.iyeyo("")).isEmpty();
    }
}

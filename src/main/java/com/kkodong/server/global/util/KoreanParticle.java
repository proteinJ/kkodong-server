package com.kkodong.server.global.util;

/**
 * 한국어 조사 선택. 앞말의 <b>받침 유무</b>에 따라 형태가 갈리는 조사를 붙여준다.
 *
 * <p>왜 필요한가: 추천 이유 문장이 견종 이름을 그대로 끼워 넣는데("우리 아이랑 같은 {견종}"),
 * 받침이 있으면 "푸들이에요", 없으면 "말티즈예요"다. 한쪽으로 고정하면 94종 중 절반이
 * 어색해진다. {@code docs/design/RECOMMENDATION.md} 5절의 예시가 템플릿과 어긋나 있던
 * 것도 같은 이유다(템플릿은 "{견종}이에요", 예시는 "말티즈예요").
 *
 * <p><b>성향 태그와는 다른 문제다.</b> 태그는 "장난꾸러기 → 장난기 많은"처럼 규칙으로
 * 유도할 수 없어 설정에 두 형태를 적어둔다(문자열 조작 금지). 반면 받침 유무는 유니코드
 * 한글 음절 코드에서 <b>결정적으로 계산</b>되므로 표를 들고 있을 이유가 없다.
 */
public final class KoreanParticle {

    private KoreanParticle() {}

    private static final char SYLLABLE_FIRST = '가';   // U+AC00
    private static final char SYLLABLE_LAST = '힣';    // U+D7A3
    private static final int JONGSUNG_COUNT = 28;      // 종성 자리 수(없음 포함)

    /** {@code 푸들 → 푸들이에요}, {@code 말티즈 → 말티즈예요} */
    public static String iyeyo(String word) {
        return word + (hasFinalConsonant(word) ? "이에요" : "예요");
    }

    /**
     * 마지막 글자에 받침이 있는지.
     *
     * <p>한글 음절은 U+AC00('가')부터 초성×21×28 + 중성×28 + 종성 순으로 배열된다.
     * 따라서 시작점으로부터의 거리를 28로 나눈 나머지가 곧 종성 번호이고, 0이면 받침이 없다.
     *
     * <p>마지막 글자가 한글 음절이 아니면(영문·숫자·기호) {@code false} 를 돌려준다.
     * 받침 개념이 없는 글자에 대해 "있다"고 답하는 것보다 낫고, 현재 견종 목록은 전부
     * 한글이라 실제로 타지 않는 경로다.
     */
    public static boolean hasFinalConsonant(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
        char last = word.charAt(word.length() - 1);
        if (last < SYLLABLE_FIRST || last > SYLLABLE_LAST) {
            return false;
        }
        return (last - SYLLABLE_FIRST) % JONGSUNG_COUNT != 0;
    }
}

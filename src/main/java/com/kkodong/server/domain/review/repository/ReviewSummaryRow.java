package com.kkodong.server.domain.review.repository;

/**
 * 리뷰 평점 집계 한 줄(PN-20, KG-02).
 *
 * <p><b>왜 인터페이스 프로젝션인가</b>: 평균과 건수를 한 쿼리로 받으면 반환이
 * {@code Object[]}가 되는데, 그 배열의 모양은 컴파일러가 검사해 주지 않는다.
 * 실제로 캐스팅을 잘못해도 컴파일이 통과하고 실행 시에야 ClassCastException으로 터졌다.
 * 이름 붙은 getter로 받으면 그 실수가 애초에 불가능하다.
 *
 * <p>쿼리의 별칭({@code as averageRating})과 getter 이름이 대응한다.
 */
public interface ReviewSummaryRow {

    /** 평균 평점. 리뷰가 없으면 0. */
    Double getAverageRating();

    /** 삭제되지 않은 리뷰 수. */
    long getTotalCount();
}

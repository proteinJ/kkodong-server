package com.kkodong.server.domain.friend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "friendships")
public class Friendship {

    @Id
    @GeneratedValue
    private UUID id;

    // ⚠️ V5 주석 참조 — (userAId < userBId)로 정렬해 INSERT해야
    //    uq_friendships_user_pair 가 (A,B)/(B,A) 중복을 막아준다.
    @Column(name = "user_a_id", nullable = false)
    private UUID userAId;

    @Column(name = "user_b_id", nullable = false)
    private UUID userBId;

    // userAId 정렬에 맞춰 dogAId 는 userAId 의 강아지여야 한다.
    @Column(name = "dog_a_id", nullable = false)
    private UUID dogAId;

    @Column(name = "dog_b_id", nullable = false)
    private UUID dogBId;

    // MEET-2(스침 상호 확인)로 생긴 친구는 null.
    @Column(name = "source_request_id")
    private UUID sourceRequestId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}

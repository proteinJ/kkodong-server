package com.kkodong.server.domain.friend.service;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.dog.repository.DogRepository;
import com.kkodong.server.domain.friend.domain.CursorState;
import com.kkodong.server.domain.friend.dto.RecommendationResponse;
import com.kkodong.server.domain.friend.repository.FriendshipRepository;
import com.kkodong.server.domain.friend.repository.RecommendationCandidate;
import com.kkodong.server.domain.safety.repository.BlockRepository;
import com.kkodong.server.domain.user.domain.User;
import com.kkodong.server.domain.user.repository.UserRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import com.kkodong.server.support.PropertiesFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 반경 확장·커서·페이지 자르기. 저장소는 목으로 두고 점수 계산과 이유 문장은 실제 객체를 쓴다.
 *
 * <p>목은 후보를 항상 같은 순서로 돌려주므로, 거리가 같은 후보의 DB 정렬 순서가 흔들리는
 * 문제는 여기서 잡히지 않는다. 그 보장은 {@code findCandidates} 의 {@code ORDER BY distanceMeters, d.id} 가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock private UserRepository userRepository;
    @Mock private DogRepository dogRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private FriendshipRepository friendshipRepository;

    private RecommendationService service;

    private final UUID myUserId = UUID.randomUUID();
    private final UUID myDogId = UUID.randomUUID();

    private final List<Dog> dogs = new ArrayList<>();
    private final List<User> owners = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new RecommendationService(
                PropertiesFixture.recommendation(),
                userRepository,
                dogRepository,
                blockRepository,
                friendshipRepository,
                new RecommendationScorer(PropertiesFixture.dog(), PropertiesFixture.recommendation()),
                new RecommendationReasonBuilder(PropertiesFixture.dog(), PropertiesFixture.recommendation(), PropertiesFixture.user())
        );

        User me = User.builder()
                .id(myUserId)
                .homeLocation(GEOMETRY.createPoint(new Coordinate(129.08, 35.2)))
                .build();
        Dog myDog = Dog.builder().id(myDogId).ownerId(myUserId).name("몽이").build();

        when(userRepository.findById(myUserId)).thenReturn(Optional.of(me));
        when(dogRepository.findById(myDogId)).thenReturn(Optional.of(myDog));
    }

    private void stubExclusions() {
        when(blockRepository.findRelatedUserIds(myUserId)).thenReturn(List.of());
        when(friendshipRepository.findFriendUserIds(myUserId)).thenReturn(List.of());
    }

    private List<RecommendationCandidate> candidates(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    User owner = User.builder().id(UUID.randomUUID()).displayName("견주" + i).build();
                    Dog dog = Dog.builder().id(UUID.randomUUID()).ownerId(owner.getId()).name("강아지" + i).build();
                    owners.add(owner);
                    dogs.add(dog);
                    return (RecommendationCandidate) new Row(dog.getId(), owner.getId(), 100.0 * i);
                })
                .toList();
    }

    private void stubCandidates(int radiusKm, List<RecommendationCandidate> rows) {
        when(dogRepository.findCandidates(eq(myUserId), anyDouble(), anyDouble(), eq(radiusKm * 1000), any(), anyInt()))
                .thenReturn(rows);
    }

    private void stubHydrate() {
        when(dogRepository.findAllById(anyIterable())).thenReturn(dogs);
        when(userRepository.findAllById(anyIterable())).thenReturn(owners);
    }

    private void verifyQueriedRadius(int radiusKm, int times) {
        verify(dogRepository, times(times))
                .findCandidates(eq(myUserId), anyDouble(), anyDouble(), eq(radiusKm * 1000), any(), anyInt());
    }

    @Test
    @DisplayName("1km 에서 limit 이상 나오면 확장하지 않고 1km 로 확정한다")
    void stopsAtFirstRadiusWhenEnough() {
        stubExclusions();
        stubCandidates(1, candidates(20));
        stubHydrate();

        RecommendationResponse.page page = service.recommend(myUserId, myDogId, 20, null);

        assertThat(page.appliedRadiusKm()).isEqualTo(1);
        assertThat(page.items()).hasSize(20);
        verifyQueriedRadius(1, 1);
        verifyQueriedRadius(2, 0);
    }

    @Test
    @DisplayName("모든 단계에서 limit 미만이면 1→2→3→5km 로 확장하고 5km 로 확정한다")
    void expandsToMaxRadius() {
        stubExclusions();
        stubCandidates(1, candidates(1));
        stubCandidates(2, candidates(2));
        stubCandidates(3, candidates(3));
        stubCandidates(5, candidates(4));
        stubHydrate();

        RecommendationResponse.page page = service.recommend(myUserId, myDogId, 20, null);

        assertThat(page.appliedRadiusKm()).isEqualTo(5);
        assertThat(page.items()).hasSize(4);
        assertThat(page.nextCursor()).isNull();
        verifyQueriedRadius(1, 1);
        verifyQueriedRadius(2, 1);
        verifyQueriedRadius(3, 1);
        verifyQueriedRadius(5, 1);
    }

    @Test
    @DisplayName("커서가 있으면 커서의 반경으로 한 번만 조회하고 다시 확장하지 않는다")
    void cursorRadiusIsNotRecalculated() {
        stubExclusions();
        stubCandidates(3, candidates(25));
        stubHydrate();

        String cursor = RecommendationCursor.encode(new CursorState(3, 20));
        RecommendationResponse.page page = service.recommend(myUserId, myDogId, 20, cursor);

        assertThat(page.appliedRadiusKm()).isEqualTo(3);
        assertThat(page.items()).hasSize(5);
        verifyQueriedRadius(3, 1);
        verifyQueriedRadius(1, 0);
        verifyQueriedRadius(2, 0);
        verifyQueriedRadius(5, 0);
    }

    @Test
    @DisplayName("페이지를 넘겨도 후보가 중복·누락되지 않고 마지막 페이지의 nextCursor 는 null 이다")
    void pagesDoNotOverlap() {
        stubExclusions();
        stubCandidates(1, candidates(25));
        stubHydrate();

        RecommendationResponse.page first = service.recommend(myUserId, myDogId, 20, null);
        RecommendationResponse.page second = service.recommend(myUserId, myDogId, 20, first.nextCursor());

        assertThat(first.items()).hasSize(20);
        assertThat(first.nextCursor()).isEqualTo(RecommendationCursor.encode(new CursorState(1, 20)));
        assertThat(second.items()).hasSize(5);
        assertThat(second.nextCursor()).isNull();

        Set<UUID> seen = new HashSet<>();
        first.items().forEach(item -> seen.add(item.dog().id()));
        second.items().forEach(item -> seen.add(item.dog().id()));
        assertThat(seen).hasSize(25);
    }

    @Test
    @DisplayName("후보 수가 limit 의 배수로 딱 떨어지면 마지막 페이지의 nextCursor 는 null 이다")
    void exactMultipleEndsWithNullCursor() {
        stubExclusions();
        stubCandidates(1, candidates(20));
        stubHydrate();

        RecommendationResponse.page page = service.recommend(myUserId, myDogId, 20, null);

        assertThat(page.items()).hasSize(20);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("설정에 없는 반경을 담은 커서는 INVALID_CURSOR")
    void cursorWithUnknownRadiusIsRejected() {
        String cursor = RecommendationCursor.encode(new CursorState(999, 20));

        assertThatThrownBy(() -> service.recommend(myUserId, myDogId, 20, cursor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CURSOR);
        verify(dogRepository, never()).findCandidates(any(), anyDouble(), anyDouble(), anyInt(), any(), anyInt());
    }

    @Test
    @DisplayName("후보가 없으면 빈 목록과 상한 반경을 돌려주고 hydrate 를 건너뛴다")
    void emptyPoolSkipsHydrate() {
        stubExclusions();
        stubCandidates(1, List.of());
        stubCandidates(2, List.of());
        stubCandidates(3, List.of());
        stubCandidates(5, List.of());

        RecommendationResponse.page page = service.recommend(myUserId, myDogId, 20, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.appliedRadiusKm()).isEqualTo(5);
        verify(dogRepository, never()).findAllById(anyIterable());
        verify(userRepository, never()).findAllById(anyIterable());
    }

    private record Row(UUID dogId, UUID ownerId, double distanceMeters) implements RecommendationCandidate {
        @Override public UUID getDogId() { return dogId; }
        @Override public UUID getOwnerId() { return ownerId; }
        @Override public double getDistanceMeters() { return distanceMeters; }
    }
}

package com.kkodong.server.domain.meta.service;

import com.kkodong.server.domain.meta.dto.MetaDataResponse;
import com.kkodong.server.global.config.DogProperties;
import com.kkodong.server.global.config.UserProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.kkodong.server.domain.meta.dto.MetaDataResponse.*;

@Service
@RequiredArgsConstructor
public class MetaDataService {

    private final DogProperties dogProperties;
    private final UserProperties userProperties;

    // 개수 제한은 설정값이 아니다 — DTO @Size 와 DB CHECK 제약이 담당한다.
    // 여기서는 클라이언트가 "최대 N개" UI 를 그릴 수 있게 알려주기만 한다.
    private static final int MAX_BREEDS = 1;
    private static final int MAX_TRAITS = 3;
    private static final int MAX_TIME_SLOTS = 3;

    public MetaDataResponse getMetaData() {

        return new MetaDataResponse(breed(), personality(), timeSlots());
    }

    private timeSlotOptions timeSlots() {
        List<timeSlot> slots = userProperties.walkTimeSlot().slots().entrySet().stream()
                .map(e -> new timeSlot(e.getKey(), e.getValue()))
                .toList();

        return new timeSlotOptions(MAX_TIME_SLOTS, slots);
    }

    private traitOptions personality() {
        List<String> traits = List.copyOf(dogProperties.personality().tags().keySet());

        return new traitOptions(MAX_TRAITS, traits);
    }

    private breedOptions breed() {
        List<breedGroup> groups = dogProperties.breed().groups().entrySet().stream()
                .map(e -> new breedGroup(e.getKey(),
                        e.getValue().display(),
                        e.getValue().breeds()))
                .toList();
        return new breedOptions(MAX_BREEDS, groups);
    }
}

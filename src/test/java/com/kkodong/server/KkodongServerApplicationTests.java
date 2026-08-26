package com.kkodong.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 로컬 Postgres(5433)·더미 R2 설정이 필요하다 — application-local.yml 참조.
// 프로파일을 안 주면 application.yml 기본값(localhost:5432)으로 붙어 실패한다.
@SpringBootTest
@ActiveProfiles("local")
class KkodongServerApplicationTests {

	@Test
	void contextLoads() {
	}

}

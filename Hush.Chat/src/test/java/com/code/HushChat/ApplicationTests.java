package com.code.HushChat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = {
    "app.jwt-secret=daf66e01593f61a15b857cf433aae03a005812b31234e149036bcc8dee755dbb",
    "app.jwt-expiration-milliseconds=3600000"
})
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}

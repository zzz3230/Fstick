package ru.fstick.integrationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.sql.init.mode=never")
class IntegrationServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}

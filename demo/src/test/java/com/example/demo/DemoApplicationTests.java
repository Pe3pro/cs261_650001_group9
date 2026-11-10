package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
        "spring.datasource.url=" +
            "jdbc:h2:mem:contextdb;" +
            "MODE=MySQL;" +
            "DB_CLOSE_DELAY=-1",

        "spring.datasource.driver-class-name=" +
            "org.h2.Driver",

        "spring.datasource.username=sa",
        "spring.datasource.password=",

        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",

        /*
         * Context Test ไม่จำเป็นต้องสร้างข้อมูล Demo
         */
        "app.seed-demo-data=false"
    }
)
class DemoApplicationTests {

    @Test
    void contextLoads() {
    }
}
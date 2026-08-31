package dev.specgraph.reference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;

@SpringBootTest
class ReferenceApplicationTests {
    @Test
    void contextLoads() {}

    @Test
    void moduleBoundariesAreValid() {
        ApplicationModules.of(ReferenceApplication.class).verify();
    }
}

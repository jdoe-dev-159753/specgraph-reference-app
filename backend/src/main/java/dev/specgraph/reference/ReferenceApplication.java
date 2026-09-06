package dev.specgraph.reference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runtime composition root for the reference application.
 * Domain and application contracts remain framework-neutral; Spring wiring is activated only from
 * this outer bootstrap boundary.
 */
@SpringBootApplication
public class ReferenceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReferenceApplication.class, args);
    }
}

package org.seventeenthsecond.uaofoundry;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundryApplicationTest {

    @Test
    void acceptsUnrelatedIdentitySeedsThroughTheSameExecutablePath() {
        String[] seeds = {
                "cow",
                "hydrogen",
                "granite",
                "Certificate III Electrotechnology"
        };

        for (String seed : seeds) {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            FoundryApplication app = new FoundryApplication(
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    new PrintStream(stderr, true, StandardCharsets.UTF_8)
            );

            int exit = app.run(new String[]{"manufacture", "--identity", seed});
            String output = stdout.toString(StandardCharsets.UTF_8);

            assertEquals(0, exit, () -> stderr.toString(StandardCharsets.UTF_8));
            assertTrue(output.contains("\"phase\":\"REQUEST_ACCEPTED\""));
            assertTrue(output.contains("\"publicationStatus\":\"NOT_PUBLISHED\""));
            assertTrue(output.contains("\"identitySeed\":\"" + seed + "\""));
        }
    }

    @Test
    void missingIdentityFailsClosed() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        FoundryApplication app = new FoundryApplication(
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8)
        );

        int exit = app.run(new String[]{"manufacture"});

        assertEquals(2, exit);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Missing required option: --identity"));
    }

    @Test
    void lifecycleCommandsDeclareFoundationOnly() {
        String[] commands = {"interpret", "status", "resume", "verify", "inspect"};

        for (String command : commands) {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            FoundryApplication app = new FoundryApplication(
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    System.err
            );

            int exit = app.run(new String[]{command});

            assertEquals(0, exit);
            assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("\"implementationStatus\":\"FOUNDATION_ONLY\""));
        }
    }
}

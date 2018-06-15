package com.smartbear.hiptest.client;

import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;

public class HiptestPublisherTest {

    @Test
    public void runHiptestPublisher() throws Exception {
        HiptestPublisher client = new HiptestPublisher(System.getProperty("client"), System.getProperty("token"),
                System.getProperty("uid"), Integer.parseInt(System.getProperty("projectId")));

        try (Reader gherkinInput = new InputStreamReader(getClass().getResourceAsStream("/test.feature"))) {
            client.publishGherkin(gherkinInput);
        }
        client.createTestRun("Ulysses Test run");
    }
}

package com.example.mysqllockmvccdemo.isolation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class IsolationLevelDemoTest {

    @Autowired
    private IsolationLevelDemoService isolationLevelDemoService;

    @Test
    void readCommittedAndRepeatableReadShouldShowDifferentSecondReads() {
        IsolationLevelDemoService.NonRepeatableReadResult result =
                isolationLevelDemoService.nonRepeatableReadDemo();

        assertThat(result.readCommittedFirstRead()).isEqualTo(100);
        assertThat(result.readCommittedSecondRead()).isEqualTo(120);
        assertThat(result.repeatableReadFirstRead()).isEqualTo(100);
        assertThat(result.repeatableReadSecondRead()).isEqualTo(100);
    }
}

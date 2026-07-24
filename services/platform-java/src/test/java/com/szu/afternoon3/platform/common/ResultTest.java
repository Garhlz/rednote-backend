package com.szu.afternoon3.platform.common;

import com.szu.afternoon3.platform.enums.ResultCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    void successShouldUseUnifiedBusinessCodeAndKeepData() {
        Result<String> result = Result.success("payload");

        assertThat(result.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
        assertThat(result.getMessage()).isEqualTo(ResultCode.SUCCESS.getMessage());
        assertThat(result.getData()).isEqualTo("payload");
    }

    @Test
    void enumErrorShouldUseCodeAndMessage() {
        Result<Void> result = Result.error(ResultCode.PARAM_ERROR);

        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo(ResultCode.PARAM_ERROR.getMessage());
        assertThat(result.getData()).isNull();
    }

    @Test
    void customErrorShouldPreserveCallerMessage() {
        Result<Void> result = Result.error(49999, "custom message");

        assertThat(result.getCode()).isEqualTo(49999);
        assertThat(result.getMessage()).isEqualTo("custom message");
        assertThat(result.getData()).isNull();
    }
}

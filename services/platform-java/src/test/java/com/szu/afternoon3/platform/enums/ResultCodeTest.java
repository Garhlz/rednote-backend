package com.szu.afternoon3.platform.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ResultCodeTest {

    @Test
    void codesShouldBeUniqueAndMessagesShouldBePresent() {
        Set<Integer> codes = Arrays.stream(ResultCode.values())
                .map(ResultCode::getCode)
                .collect(Collectors.toSet());

        assertThat(codes).hasSize(ResultCode.values().length);
        assertThat(ResultCode.values())
                .allSatisfy(code -> assertThat(code.getMessage()).isNotBlank());
    }

    @Test
    void errorCodePrefixShouldMatchHttpStatusConvention() {
        assertThat(ResultCode.SUCCESS.getCode()).isEqualTo(200);

        Arrays.stream(ResultCode.values())
                .filter(code -> code != ResultCode.SUCCESS)
                .forEach(code -> {
                    int httpPrefix = code.getCode() / 100;
                    assertThat(httpPrefix)
                            .as(code.name())
                            .isBetween(400, 599);
                });
    }
}

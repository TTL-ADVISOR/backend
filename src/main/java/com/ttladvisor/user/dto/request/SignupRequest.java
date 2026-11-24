package com.ttladvisor.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 비밀번호 확인은 화면에서만 검사하므로 받지 않는다.
 */
public record SignupRequest(
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        String password,

        @Size(max = 100, message = "회사는 100자 이하여야 합니다.")
        String company) {
}

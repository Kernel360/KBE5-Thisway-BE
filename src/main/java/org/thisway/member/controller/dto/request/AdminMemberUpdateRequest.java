package org.thisway.member.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.member.service.dto.input.AdminMemberUpdateInput;

public record AdminMemberUpdateRequest(

        @NotBlank
        String name,

        @NotBlank
        String email,

        @NotBlank
        String phone,

        @NotNull
        String memo
) {

    public AdminMemberUpdateInput toMemberUpdateInput(long id) {
        return AdminMemberUpdateInput.builder()
                .id(id)
                .name(name)
                .email(email)
                .phone(phone)
                .memo(memo)
                .build();
    }
}

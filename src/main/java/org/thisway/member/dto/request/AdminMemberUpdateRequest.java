package org.thisway.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.thisway.member.dto.AdminMemberUpdateInput;

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

    public AdminMemberUpdateInput toMemberUpdateDto(long id) {
        return AdminMemberUpdateInput.builder()
                .id(id)
                .name(name)
                .email(email)
                .phone(phone)
                .memo(memo)
                .build();
    }
}

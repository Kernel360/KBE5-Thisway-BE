package org.thisway.member.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import org.thisway.member.application.CompanyChefMemberUpdateInput;

public record CompanyChefMemberUpdateRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 255)
        String email,

        @NotBlank
        String phone,

        @NotNull
        @Size(max = 255)
        String memo
) {

    public CompanyChefMemberUpdateInput toMemberUpdateInput(long id) {
        return CompanyChefMemberUpdateInput.builder()
                .id(id)
                .name(name)
                .email(email)
                .phone(phone)
                .memo(memo)
                .build();
    }
}

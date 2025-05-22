package org.thisway.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.common.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// todo: 역할 컬럼 추가
public class Member extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String memo;

    @Builder
    public Member(
            String name,
            String email,
            String password,
            String phone,
            String memo
    ) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.memo = memo;
    }
}

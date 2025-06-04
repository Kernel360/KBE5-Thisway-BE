package org.thisway.member.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.common.BaseEntity;
import org.thisway.company.entity.Company;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    MemberRole role;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "phone", nullable = false))
    private PhoneNumber phone;

    @Column(nullable = false)
    private String memo;

    @Builder
    public Member(
            Company company,
            MemberRole role,
            String name,
            String email,
            String password,
            String phone,
            String memo
    ) {
        this.company = company;
        this.role = role;
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = new PhoneNumber(phone);
        this.memo = memo;
    }

    public String getPhoneValue() {
        return phone.getValue();
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public Set<MemberRole> getLowerOrEqualRoles() {
        return role.getLowerOrEqualRoles();
    }
}

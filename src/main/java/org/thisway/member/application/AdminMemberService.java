package org.thisway.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;
import org.thisway.company.domain.Company;
import org.thisway.company.intrastructure.CompanyRepository;
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.infrastructure.MemberRepository;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminMemberService {

    private static final Set<MemberRole> ADMIN_ACCESS_AUTHORITIES = Set.of(
            MemberRole.ADMIN,
            MemberRole.COMPANY_CHEF
    );
    private static final Set<MemberRole> ADMIN_REGISTER_AUTHORITIES = Set.of(
            MemberRole.ADMIN,
            MemberRole.COMPANY_CHEF
    );

    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AdminMemberDetailOutput getMemberDetail(Long id) {
        return AdminMemberDetailOutput.from(getActiveMember(id));
    }

    @Transactional(readOnly = true)
    public AdminMembersOutput getMembers(Pageable pageable) {
        Page<Member> members = memberRepository.findAllByActiveTrueAndRoleIn(ADMIN_ACCESS_AUTHORITIES, pageable);

        return AdminMembersOutput.from(members);
    }

    public void registerMember(AdminMemberRegisterInput request) {
        String encodePassword = passwordEncoder.encode(request.password());
        Company company = companyRepository.findByIdAndActiveTrue(request.companyId())
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
        validateEmail(request.email());

        if (!ADMIN_REGISTER_AUTHORITIES.contains(request.role())) {
            throw new CustomException(ErrorCode.MEMBER_REGISTER_DENIED);
        }

        Member member = Member.builder()
                .company(company)
                .role(request.role())
                .name(request.name())
                .email(request.email())
                .password(encodePassword)
                .phone(request.phone())
                .memo(request.memo())
                .build();

        memberRepository.save(member);
    }

    public void updateMember(AdminMemberUpdateInput request) {
        Member member = getActiveMember(request.id());

        if (!member.getEmail().equals(request.email())) {
            validateEmail(request.email());
        }

        member.updateName(request.name());
        member.updateEmail(request.email());
        member.updatePhone(request.phone());
        member.updateMemo(request.memo());
    }

    public void deleteMember(Long id) {
        Member member = getActiveMember(id);

        member.delete();
    }

    private void validateEmail(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
        }
    }

    private Member getActiveMember(long id) {
        Member member = memberRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        if (!ADMIN_ACCESS_AUTHORITIES.contains(member.getRole())) {
            throw new CustomException(ErrorCode.MEMBER_ACCESS_DENIED);
        }

        return member;
    }
}

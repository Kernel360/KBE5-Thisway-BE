package org.thisway.member.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.member.service.dto.output.CompanyChefMemberDetailOutput;
import org.thisway.member.service.dto.input.CompanyChefMemberRegisterInput;
import org.thisway.member.service.dto.output.CompanyChefMemberSummaryOutput;
import org.thisway.member.service.dto.input.CompanyChefMemberUpdateInput;
import org.thisway.member.service.dto.output.CompanyChefMembersOutput;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.repository.MemberRepository;
import org.thisway.security.service.SecurityService;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyChefMemberService {

    private static final Set<MemberRole> COMPANY_CHEF_ACCESS_AUTHORITIES = Set.of(
            MemberRole.COMPANY_CHEF,
            MemberRole.COMPANY_ADMIN,
            MemberRole.MEMBER
    );

    private static final Set<MemberRole> COMPANY_CHEF_REGISTER_AUTHORITIES = Set.of(
            MemberRole.COMPANY_CHEF,
            MemberRole.COMPANY_ADMIN,
            MemberRole.MEMBER
    );

    private final SecurityService securityService;
    private final PasswordEncoder passwordEncoder;

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public CompanyChefMemberDetailOutput getMemberDetail(Long id) {
        return CompanyChefMemberDetailOutput.from(getActiveMember(id));
    }

    @Transactional(readOnly = true)
    public CompanyChefMembersOutput getMembers(Pageable pageable) {
        long authenticatedMemberCompanyId = securityService.getCurrentMemberDetails().getCompanyId();
        Page<Member> members = memberRepository.findAllByActiveTrueAndRoleInAndCompanyId(
                COMPANY_CHEF_ACCESS_AUTHORITIES, authenticatedMemberCompanyId, pageable
        );

        return CompanyChefMembersOutput.from(members);
    }

    public void registerMember(CompanyChefMemberRegisterInput request) {
        String encodePassword = passwordEncoder.encode(request.password());
        validateEmail(request.email());

        if (!COMPANY_CHEF_REGISTER_AUTHORITIES.contains(request.role())) {
            throw new CustomException(ErrorCode.MEMBER_REGISTER_DENIED);
        }

        Company company = securityService.getCurrentMember().getCompany();
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

    public void updateMember(CompanyChefMemberUpdateInput request) {
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

    public CompanyChefMemberSummaryOutput summary() {
        long companyId = securityService.getCurrentMemberDetails().getCompanyId();

        long companyChefCount = countActiveAndCompanyIdAndRole(companyId, MemberRole.COMPANY_CHEF);
        long companyAdminCount = countActiveAndCompanyIdAndRole(companyId, MemberRole.COMPANY_ADMIN);
        long memberCount = countActiveAndCompanyIdAndRole(companyId, MemberRole.MEMBER);

        return CompanyChefMemberSummaryOutput.builder()
                .companyChefCount(companyChefCount)
                .companyAdminCount(companyAdminCount)
                .memberCount(memberCount)
                .build();
    }

    private Member getActiveMember(long id) {
        Member member = memberRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        long authenticatedMemberCompanyId = securityService.getCurrentMemberDetails().getCompanyId();
        if (!COMPANY_CHEF_ACCESS_AUTHORITIES.contains(member.getRole())
                || authenticatedMemberCompanyId != member.getCompany().getId()
        ) {
            throw new CustomException(ErrorCode.MEMBER_ACCESS_DENIED);
        }

        return member;
    }

    private void validateEmail(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
        }
    }

    private long countActiveAndCompanyIdAndRole(long companyId, MemberRole role) {
        return memberRepository.countByActiveTrueAndCompanyIdAndRole(companyId, role);
    }
}

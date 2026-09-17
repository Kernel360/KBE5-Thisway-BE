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
import org.thisway.member.domain.Member;
import org.thisway.member.domain.MemberRole;
import org.thisway.member.infrastructure.MemberRepository;
import org.thisway.support.security.service.SecurityService;

import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.thisway.member.util.EmailValidation;
import org.thisway.member.util.PasswordValidation;

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
    public CompanyChefMembersOutput getMembers(
            Pageable pageable,
            CompanyChefMemberSearchCriteria criteria
    ) {
        long authenticatedMemberCompanyId = securityService.getCurrentMemberDetails().getCompanyId();
        Page<Member> members = memberRepository.searchActiveMembers(
                COMPANY_CHEF_ACCESS_AUTHORITIES,
                authenticatedMemberCompanyId,
                criteria,
                pageable);

        return CompanyChefMembersOutput.from(members);
    }

    public void registerMember(CompanyChefMemberRegisterInput request) {
        validateFields(request.name(), request.email(), request.memo());
        if (!PasswordValidation.isValidPassword(request.password())) {
            throw new CustomException(ErrorCode.MEMBER_INVALID_PASSWORD);
        }
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

        flushEmailWrite(() -> memberRepository.saveAndFlush(member));
    }

    public void updateMember(CompanyChefMemberUpdateInput request) {
        Member member = getActiveMember(request.id());

        validateFields(request.name(), request.email(), request.memo());
        if (memberRepository.existsByEmailAndIdNot(request.email(), member.getId())) {
            throw new CustomException(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
        }

        member.updateName(request.name());
        member.updateEmail(request.email());
        member.updatePhone(request.phone());
        member.updateMemo(request.memo());
        flushEmailWrite(memberRepository::flush);
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
        long authenticatedMemberCompanyId = securityService.getCurrentMemberDetails().getCompanyId();
        Member member = memberRepository.findByIdAndCompanyIdAndActiveTrue(id, authenticatedMemberCompanyId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        if (!COMPANY_CHEF_ACCESS_AUTHORITIES.contains(member.getRole())) {
            throw new CustomException(ErrorCode.MEMBER_ACCESS_DENIED);
        }

        return member;
    }

    private void validateFields(String name, String email, String memo) {
        if (name == null || name.isBlank() || name.length() > 255 || memo == null || memo.length() > 255) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!EmailValidation.isValidEmail(email) || email.length() > 255) {
            throw new CustomException(ErrorCode.MEMBER_INVALID_EMAIL);
        }
    }

    private void flushEmailWrite(Runnable write) {
        try {
            write.run();
        } catch (DataIntegrityViolationException failure) {
            // Translate only the known email unique constraint; unrelated DB failures must remain failures.
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && constraint.getConstraintName() != null
                        && constraint.getConstraintName().toLowerCase(java.util.Locale.ROOT)
                                .contains("ukmbmcqelty0fbrvxp1q58dn57t")) {
                    throw new CustomException(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
                }
            }
            throw failure;
        }
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

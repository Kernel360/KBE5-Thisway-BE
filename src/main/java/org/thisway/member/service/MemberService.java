package org.thisway.member.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.BaseEntity;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.member.dto.MemberSummaryDto;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.dto.response.MemberResponse;
import org.thisway.member.dto.response.MembersResponse;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.repository.MemberRepository;
import org.thisway.security.dto.request.MemberDetails;
import org.thisway.security.service.SecurityService;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final SecurityService securityService;
    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MemberResponse getMemberDetail(Long id) {
        return memberRepository.findById(id)
                .filter(BaseEntity::isActive)
                .map(MemberResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public MembersResponse getMembers(Pageable pageable) {
        Member currentMember = securityService.getCurrentMember();

        Page<Member> members;
        if (currentMember.getRole() != MemberRole.ADMIN) {
            Set<MemberRole> targetRoles = currentMember.getLowerOrEqualRoles();
            members = memberRepository.findAllByActiveTrueAndRoleInAndCompany(targetRoles, currentMember.getCompany(),
                    pageable);
        } else {
            members = memberRepository.findAllByActiveTrue(pageable);
        }

        return MembersResponse.from(members);
    }

    public void registerMember(MemberRegisterRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
        }

        Company company = companyRepository.findById(request.companyId())
                .filter(BaseEntity::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        String encryptedPassword = passwordEncoder.encode(request.password());

        Member member = request.toMember(company, encryptedPassword);

        memberRepository.save(member);
    }

    public void deleteMember(Long id) {
        memberRepository.findById(id)
                .filter(BaseEntity::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND))
                .delete();
    }

    public MemberSummaryDto summary() {
        MemberDetails currentMemberDetails = securityService.getCurrentMemberDetails();
        long companyId = currentMemberDetails.getCompanyId();

        long companyAdminCount = countActiveAndCompanyIdAndRole(companyId, MemberRole.ADMIN);
        long companyChefCount = countActiveAndCompanyIdAndRole(companyId, MemberRole.COMPANY_CHEF);
        long memberCount = countActiveAndCompanyIdAndRole(companyId, MemberRole.MEMBER);

        return MemberSummaryDto.builder()
                .companyAdminCount(companyAdminCount)
                .companyChefCount(companyChefCount)
                .memberCount(memberCount)
                .build();
    }

    private long countActiveAndCompanyIdAndRole(long companyId, MemberRole role) {
        return memberRepository.countByActiveTrueAndCompanyIdAndRole(companyId, role);
    }
}

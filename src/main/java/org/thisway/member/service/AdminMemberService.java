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
import org.thisway.company.repository.CompanyRepository;
import org.thisway.member.dto.AdminMemberRegisterDto;
import org.thisway.member.dto.MemberDto;
import org.thisway.member.dto.MembersDto;
import org.thisway.member.entity.Member;
import org.thisway.member.entity.MemberRole;
import org.thisway.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminMemberService {

    private static final Set<MemberRole> ADMIN_ACCESS_AUTHORITIES = Set.of(
            MemberRole.ADMIN,
            MemberRole.COMPANY_CHEF
    );

    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MemberDto getMemberDetail(Long id) {
        MemberDto member = memberRepository.findByIdAndActiveTrue(id)
                .map(MemberDto::from)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        if (!ADMIN_ACCESS_AUTHORITIES.contains(member.role())) {
            throw new CustomException(ErrorCode.MEMBER_ACCESS_DENIED);
        }

        return member;
    }

    @Transactional(readOnly = true)
    public MembersDto getMembers(Pageable pageable) {
        Page<Member> members = memberRepository.findAllByActiveTrueAndRole(MemberRole.COMPANY_CHEF, pageable);

        return MembersDto.from(members);
    }

    public void registerMember(AdminMemberRegisterDto request) {
        String encodePassword = passwordEncoder.encode(request.password());
        Company company = companyRepository.findByIdAndActiveTrue(request.companyId())
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
        if (memberRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
        }

        Member member = Member.builder()
                .company(company)
                .role(MemberRole.COMPANY_CHEF)
                .name(request.name())
                .email(request.email())
                .password(encodePassword)
                .phone(request.phone())
                .memo(request.memo())
                .build();

        memberRepository.save(member);
    }
}

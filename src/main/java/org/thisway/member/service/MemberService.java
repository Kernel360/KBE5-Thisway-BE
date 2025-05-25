package org.thisway.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thisway.common.BaseEntity;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.dto.response.MemberResponse;
import org.thisway.member.dto.response.MembersResponse;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public MemberResponse getMemberDetail(Long id) {
        return memberRepository.findById(id)
                .filter(BaseEntity::isActive)
                .map(MemberResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public MembersResponse getMembers(Pageable pageable) {
        return MembersResponse.from(memberRepository.findAllByActiveTrue(pageable));
    }

    public void registerMember(MemberRegisterRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
        }

        Company company = companyRepository.findById(request.companyId())
                .filter(BaseEntity::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        Member member = request.toMember(company);

        memberRepository.save(member);
    }

    public void deleteMember(Long id) {
        memberRepository.findById(id)
                .filter(BaseEntity::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND))
                .delete();
    }
}

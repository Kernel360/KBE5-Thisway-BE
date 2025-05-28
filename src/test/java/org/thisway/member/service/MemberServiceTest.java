package org.thisway.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.company.support.CompanyFixture;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.dto.response.MemberResponse;
import org.thisway.member.dto.response.MembersResponse;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;
import org.thisway.member.support.MemberFixture;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
class MemberServiceTest {

    private final MemberService memberService;
    private final MemberRepository memberRepository;
    @Autowired
    private CompanyRepository companyRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("멤버가 정상적으로 조회된다.")
    void 멤버_조회_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = MemberFixture.createMember(company);

        // when
        memberRepository.save(member);
        MemberResponse memberResponse = memberService.getMemberDetail(member.getId());

        // then
        assertThat(memberResponse.id()).isEqualTo(member.getId());
        assertThat(memberResponse.email()).isEqualTo(member.getEmail());
        assertThat(memberResponse.phone()).isEqualTo(member.getPhoneValue());
    }

    @Test
    @DisplayName("없는 멤버를 조회하려 하면 member not found exception이 발생한다.")
    void 멤버_조회_테스트_없는_멤버() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = MemberFixture.createMember(company);

        // when
        memberRepository.save(member);
        CustomException e = assertThrows(
                CustomException.class,
                () -> memberService.getMemberDetail(member.getId() + 1)
        );

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        // then
    }

    @Test
    @DisplayName("멤버가 페이징 정보에 맞게 정상적으로 조회된다")
    void 멤버_페이징_조회_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        List<Member> members = List.of(
                MemberFixture.createMemberWithEmail(company, "hong1@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong2@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong3@example.com"),
                MemberFixture.createMemberWithEmail(company, "hong4@example.com")
        );

        // when
        memberRepository.saveAll(members);
        MembersResponse membersResponse = memberService.getMembers(PageRequest.of(0, 2));

        // then
        assertThat(membersResponse.memberResponses().getTotalElements()).isEqualTo(members.size());
        assertThat(membersResponse.memberResponses().getNumberOfElements()).isEqualTo(2);
        assertThat(membersResponse.memberResponses().getSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("멤버 등록이 정상적으로 등록된다.")
    void 멤버_등록_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        MemberRegisterRequest request = MemberFixture.createMemberRegisterRequestWithCompanyId(company.getId());

        // when
        memberService.registerMember(request);

        // then
        List<Member> allMember = memberRepository.findAll();
        Member savedMember = allMember.getFirst();

        assertThat(allMember).hasSize(1);
        assertThat(savedMember.getId()).isNotNull();
        assertThat(savedMember.getEmail()).isEqualTo(request.email());
        assertThat(savedMember.getPassword()).isEqualTo(request.password());
        assertThat(savedMember.getPhoneValue()).isEqualTo(request.phone());
    }

    @Test
    @DisplayName("멤버_등록시_존재하는_이메일의_회원일_경우_예외가_발생한다")
    void 멤버_등록_테스트_존재하는_이메일() {
        // given
        String email = "hong@example.com";
        Company company = companyRepository.save(CompanyFixture.createCompany());
        MemberRegisterRequest request = MemberFixture.createMemberRegisterRequestWithCompanyIdAndEmail(company.getId(), email);

        // when & then
        memberRepository.save(MemberFixture.createMemberWithEmail(company, email));
        CustomException e = assertThrows(CustomException.class, () -> memberService.registerMember(request));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
    }

    @Test
    @DisplayName("멤버가 정상적으로 삭제된다.")
    void 멤버_삭제_테스트_성공() {
        // given
        Company company = companyRepository.save(CompanyFixture.createCompany());
        Member member = MemberFixture.createMember(company);

        // when
        memberRepository.save(member);

        memberService.deleteMember(member.getId());

        // then
        Member deletedMember = memberRepository.findById(member.getId()).orElse(null);
        assertThat(deletedMember).isNotNull();
        assertThat(deletedMember.isActive()).isFalse();
    }

    @Test
    @DisplayName("없는 멤버를 삭제하려 하면 member not found exception이 발생한다.")
    void 멤버_삭제_테스트_없는_멤버() {
        // when & then
        CustomException e = assertThrows(CustomException.class, () -> memberService.deleteMember(1L));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

}

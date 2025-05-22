package org.thisway.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.dto.request.MemberRegisterRequest;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;
import org.thisway.member.support.MemberFixture;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@RequiredArgsConstructor
@TestConstructor(autowireMode = AutowireMode.ALL)
class MemberServiceTest {

    private final MemberService memberService;
    private final MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("멤버 등록이 정상적으로 등록된다.")
    void givenValidRequest_whenRegisterMember_thenDoesNotThrowAnyException() {
        // given
        MemberRegisterRequest request = MemberFixture.createMemberRegisterRequest();

        // when
        memberService.registerMember(request);

        // then
        List<Member> allMember = memberRepository.findAll();
        Member savedMember = allMember.getFirst();

        assertThat(allMember).hasSize(1);
        assertThat(savedMember.getId()).isNotNull();
        assertThat(savedMember.getEmail()).isEqualTo(request.email());
        assertThat(savedMember.getPassword()).isEqualTo(request.password());
        assertThat(savedMember.getPhone()).isEqualTo(request.phone());
    }

    @Test
    @DisplayName("멤버가 정상적으로 삭제된다.")
    void givenValidMemberId_whenDeleteMember_thenSuccessfulDelete() {
        // given
        Member member = MemberFixture.createMember();

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
    void givenNotFoundMemberId_whenDeleteMember_thenThrowNotFoundException() {
        // when & then
        CustomException e = assertThrows(CustomException.class, () -> memberService.deleteMember(1L));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

}

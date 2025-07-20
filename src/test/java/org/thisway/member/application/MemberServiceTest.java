package org.thisway.member.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.thisway.member.domain.*;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberReader memberReader;

    @Mock
    private MemberStore memberStore;

    @Mock
    private CompanyClient companyClient;

    @Spy
    private final MemberCommandMapper memberCommandMapper = new MemberCommandMapper();

    @Mock
    private MemberQueryMapper memberQueryMapper;

    @InjectMocks
    private MemberService memberService;

    @Test
    @DisplayName("멤버를 등록할 수 있다.")
    void 멤버를_등록할_수_있다() {
        // given
        long companyId = 1L;
        String registerEmail = "email@email.com";
        MemberCommand.RegisterMember command = MemberCommand.RegisterMember.builder()
                .companyId(companyId)
                .role(MemberRole.COMPANY_CHEF)
                .name("name")
                .email(registerEmail)
                .password("password")
                .phone("01012345678")
                .memo("memo")
                .build();
        String actorEmail = "actor@thisway.com";

        Member mockActor = mock(Member.class);

        given(mockActor.canAccess(any(Member.class))).willReturn(true);
        given(companyClient.existById(companyId)).willReturn(true);
        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);
        given(memberReader.existByEmail(registerEmail)).willReturn(false);

        // when & then
        assertThatCode(() -> memberService.registerMember(command, actorEmail))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("없는 회사의 멤버를 등록할 수 없다.")
    void 없는_회사의_멤버를_등록할_수_없다() {
        // given
        long companyId = 1L;
        String registerEmail = "email@email.com";
        MemberCommand.RegisterMember command = MemberCommand.RegisterMember.builder()
                .companyId(companyId)
                .role(MemberRole.COMPANY_CHEF)
                .name("name")
                .email(registerEmail)
                .password("password")
                .phone("01012345678")
                .memo("memo")
                .build();
        String actorEmail = "actor@thisway.com";

        Member mockActor = mock(Member.class);

        given(companyClient.existById(companyId)).willReturn(false);
        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);

        // when
        CustomException e = catchThrowableOfType(CustomException.class, () -> memberService.registerMember(command, actorEmail));

        // then
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("접근 권한이 없는 멤버를 등록할 수 없다.")
    void 접근_권한이_없는_멤버를_등록할_수_없다() {
        // given
        long companyId = 1L;
        String registerEmail = "email@email.com";
        MemberCommand.RegisterMember command = MemberCommand.RegisterMember.builder()
                .companyId(companyId)
                .role(MemberRole.COMPANY_CHEF)
                .name("name")
                .email(registerEmail)
                .password("password")
                .phone("01012345678")
                .memo("memo")
                .build();
        String actorEmail = "actor@thisway.com";

        Member mockActor = mock(Member.class);

        given(mockActor.canAccess(any(Member.class))).willReturn(false);
        given(companyClient.existById(companyId)).willReturn(true);
        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);

        // when
        CustomException e = catchThrowableOfType(CustomException.class, () -> memberService.registerMember(command, actorEmail));

        // then
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_REGISTER_DENIED);
    }

    @Test
    @DisplayName("존재하는 이메일로 새로운 멤버를 등록할 수 없다.")
    void 존재하는_이메일로_새로운_멤버를_등록할_수_없다() {
        // given
        long companyId = 1L;
        String registerEmail = "email@email.com";
        MemberCommand.RegisterMember command = MemberCommand.RegisterMember.builder()
                .companyId(companyId)
                .role(MemberRole.COMPANY_CHEF)
                .name("name")
                .email(registerEmail)
                .password("password")
                .phone("01012345678")
                .memo("memo")
                .build();
        String actorEmail = "actor@thisway.com";

        Member mockActor = mock(Member.class);

        given(mockActor.canAccess(any(Member.class))).willReturn(false);
        given(companyClient.existById(companyId)).willReturn(true);
        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);

        // when
        CustomException e = catchThrowableOfType(CustomException.class, () -> memberService.registerMember(command, actorEmail));

        // then
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_REGISTER_DENIED);
    }

    @Test
    @DisplayName("회사 정보와 함께 멤버 정보를 조회할 수 있다.")
    void 회사_정보와_함께_멤버_정보를_조회할_수_있다() {
        // given
        long retrieveId = 1L;
        String actorEmail = "actor@mail.com";
        long companyId = 1L;

        Member mockActor = mock(Member.class);
        MemberInfo.MemberWithCompany mockMemberWithCompany = mock(MemberInfo.MemberWithCompany.class);
        CompanyInfo mockCompanyInfo = mock(CompanyInfo.class);
        MemberRole mockMemberRole = mock(MemberRole.class);

        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);
        given(mockMemberWithCompany.role()).willReturn(mockMemberRole);
        given(mockMemberWithCompany.companyInfo()).willReturn(mockCompanyInfo);
        given(mockCompanyInfo.id()).willReturn(companyId);
        given(memberReader.getMemberWithCompany(retrieveId)).willReturn(mockMemberWithCompany);
        given(mockActor.canAccess(mockMemberRole, companyId)).willReturn(true);

        // when
        MemberInfo.MemberWithCompany result = memberService.retrieveMemberWithCompany(retrieveId, actorEmail);

        // then
        assertThat(result).isEqualTo(mockMemberWithCompany);
    }

    @Test
    @DisplayName("회사 정보와 함께 멤버 정보를 조회할 떄 조회 권한이 없으면 조회할 수 없다.")
    void 회사_정보와_함께_멤버_정보를_조회할_떄_조회_권한이_없으면_조회할_수_없다() {
        // given
        long retrieveId = 1L;
        String actorEmail = "actor@mail.com";
        long companyId = 1L;

        Member mockActor = mock(Member.class);
        MemberInfo.MemberWithCompany mockMemberWithCompany = mock(MemberInfo.MemberWithCompany.class);
        CompanyInfo mockCompanyInfo = mock(CompanyInfo.class);
        MemberRole mockMemberRole = mock(MemberRole.class);

        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);
        given(mockMemberWithCompany.role()).willReturn(mockMemberRole);
        given(mockMemberWithCompany.companyInfo()).willReturn(mockCompanyInfo);
        given(mockCompanyInfo.id()).willReturn(companyId);
        given(memberReader.getMemberWithCompany(retrieveId)).willReturn(mockMemberWithCompany);
        given(mockActor.canAccess(mockMemberRole, companyId)).willReturn(false);

        // when
        CustomException e = catchThrowableOfType(
                CustomException.class,
                () -> memberService.retrieveMemberWithCompany(retrieveId, actorEmail)
        );

        // then
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("회사 정보와 함께 멤버 정보를 페이징 조회할 수 있다.")
    void 회사_정보와_함께_멤버_정보를_페이징_조회할_수_있다() {
        // given
        String actorEmail = "actor@mail.com";
        MemberRole actorRole = MemberRole.ADMIN;

        Member mockActor = mock(Member.class);
        Pageable pageable = mock(Pageable.class);
        Page<MemberInfo.MemberWithCompany> expectedResult = mock(Page.class);

        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);
        given(mockActor.getRole()).willReturn(actorRole);
        given(memberReader.getMembersWithCompanyByRoleIn(actorRole.getAccessibleRoles(), pageable)).willReturn(expectedResult);

        // when
        Page<MemberInfo.MemberWithCompany> result = memberService.retrieveMembersWithCompany(actorEmail, pageable);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    @DisplayName("회사 정보와 함께 특정 조건의 멤버 정보를 페이징 조회할 수 있다.")
    void 회사_정보와_함께_특정_조건의_멤버_정보를_페이징_조회할_수_있다() {
        // given
        String actorEmail = "actor@mail.com";
        MemberRole actorRole = MemberRole.ADMIN;

        MemberQuery.SearchMember searchQuery = mock(MemberQuery.SearchMember.class);
        MemberQuery.SearchMember overrideSearchQuery = mock(MemberQuery.SearchMember.class);
        Member mockActor = mock(Member.class);
        Pageable pageable = mock(Pageable.class);
        Page<MemberInfo.MemberWithCompany> expectedResult = mock(Page.class);

        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);
        given(mockActor.getRole()).willReturn(actorRole);
        given(memberQueryMapper.overrideRoles(searchQuery, actorRole.getAccessibleRoles())).willReturn(overrideSearchQuery);
        given(memberReader.getMembersWithCompany(overrideSearchQuery, pageable)).willReturn(expectedResult);

        // when
        Page<MemberInfo.MemberWithCompany> result = memberService.retrieveMembersWithCompany(searchQuery, actorEmail, pageable);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    @DisplayName("멤버 정보를 업데이트 할 수 있다.")
    void 멤버_정보를_업데이트_할_수_있다() {
        //given
        MemberCommand.UpdateMember command = MemberCommand.UpdateMember.builder()
                .id(1L)
                .name("name")
                .email("email")
                .phone("01012345678")
                .memo("memo")
                .build();
        String actorEmail = "actor@email.com";

        Member mockMember = mock(Member.class);
        Member mockActorMember = mock(Member.class);

        given(memberReader.getMember(command.getId())).willReturn(mockMember);
        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActorMember);
        given(mockActorMember.canAccess(mockMember)).willReturn(true);
        given(memberReader.existByEmail(command.getEmail())).willReturn(false);

        // when & then
        assertThatCode(() -> memberService.updateMember(command, actorEmail))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("멤버 정보를 업데이트 할 때 권한이 없는 경우 멤버 정보를 업데이트 할 수 없다.")
    void 멤버_정보를_업데이트_할_때_권한이_없는_경우_멤버_정보를_업데이트_할_수_없다() {
        //given
        MemberCommand.UpdateMember command = MemberCommand.UpdateMember.builder()
                .id(1L)
                .name("name")
                .email("email")
                .phone("01012345678")
                .memo("memo")
                .build();
        String actorEmail = "actor@email.com";

        Member mockMember = mock(Member.class);
        Member mockActorMember = mock(Member.class);

        given(memberReader.getMember(command.getId())).willReturn(mockMember);
        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActorMember);
        given(mockActorMember.canAccess(mockMember)).willReturn(false);

        // when
        CustomException e = catchThrowableOfType(
                CustomException.class,
                () -> memberService.updateMember(command, actorEmail)
        );

        // then
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("멤버 정보를 업데이트 할 때 이미 존재하는 이메일인 경우 멤버 정보를 업데이트 할 수 없다.")
    void 멤버_정보를_업데이트_할_때_이미_존재하는_이메일인_멤버_정보를_업데이트_할_수_없다() {
        //given
        String existEmail = "existEmail";
        MemberCommand.UpdateMember command = MemberCommand.UpdateMember.builder()
                .id(1L)
                .name("name")
                .email(existEmail)
                .phone("01012345678")
                .memo("memo")
                .build();
        String actorEmail = "actor@email.com";

        Member mockMember = mock(Member.class);
        Member mockActorMember = mock(Member.class);

        given(memberReader.getMember(command.getId())).willReturn(mockMember);
        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActorMember);
        given(mockActorMember.canAccess(mockMember)).willReturn(true);
        given(memberReader.existByEmail(existEmail)).willReturn(true);

        // when
        CustomException e = catchThrowableOfType(
                CustomException.class,
                () -> memberService.updateMember(command, actorEmail)
        );

        // then
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ALREADY_EXIST_BY_EMAIL);
    }

    @Test
    @DisplayName("멤버를 삭제할 수 있다.")
    void 멤버를_삭제할_수_있다() {
        // given
        long deleteMemberId = 1L;
        String actorEmail = "actor@thisway.com";

        Member mockDeleteMember = mock(Member.class);
        Member mockActor = mock(Member.class);

        given(memberReader.getMember(deleteMemberId)).willReturn(mockDeleteMember);
        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);
        given(mockActor.canAccess(mockDeleteMember)).willReturn(true);

        // when & then
        assertThatCode(() -> memberService.deleteMember(deleteMemberId, actorEmail))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("접근 권한이 없는 멤버를 삭제할 수 없다.")
    void 접근_권한이_없는_멤버를_삭제할_수_없다() {
        // given
        long deleteMemberId = 1L;
        String actorEmail = "actor@thisway.com";

        Member mockDeleteMember = mock(Member.class);
        Member mockActor = mock(Member.class);

        given(memberReader.getMember(deleteMemberId)).willReturn(mockDeleteMember);
        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);
        given(mockActor.canAccess(mockDeleteMember)).willReturn(false);

        // when
        CustomException e = catchThrowableOfType(
                CustomException.class,
                () -> memberService.deleteMember(deleteMemberId, actorEmail)
        );

        // then
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("회사별 멤버 요약을 조회할 수 있다.")
    void 회사별_멤버_요약을_조회할_수_있다() {
        // given
        long companyId = 1;
        String actorEmail = "actorEmail";
        long expectedCompanyChefCount = 3;
        long expectedAdminCount = 4;
        long expectedMemberCount = 5;

        Member mockActor = mock(Member.class);
        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);
        given(mockActor.getCompanyId()).willReturn(companyId);
        given(memberReader.countMemberByCompanyIdAndRole(companyId, MemberRole.COMPANY_CHEF)).willReturn(expectedCompanyChefCount);
        given(memberReader.countMemberByCompanyIdAndRole(companyId, MemberRole.COMPANY_ADMIN)).willReturn(expectedAdminCount);
        given(memberReader.countMemberByCompanyIdAndRole(companyId, MemberRole.MEMBER)).willReturn(expectedMemberCount);

        // when
        MemberInfo.MemberSummary result = memberService.summaryCompanyMember(companyId, actorEmail);

        // then
        assertThat(result.companyChefCount()).isEqualTo(expectedCompanyChefCount);
        assertThat(result.companyAdminCount()).isEqualTo(expectedAdminCount);
        assertThat(result.memberCount()).isEqualTo(expectedMemberCount);
    }

    @Test
    @DisplayName("회사별 멤버 요약 호출자가 다른 회사면 회사별 멤버 요약을 조회할 수 없다.")
    void 회사별_멤버_요약_호출자가_다른_회사면_회사별_멤버_요약을_조회할_수_없다() {
        // given
        long companyId = 1;
        long anotherCompanyId = 2;
        String actorEmail = "actorEmail";

        Member mockActor = mock(Member.class);
        given(memberReader.getMemberByEmail(actorEmail)).willReturn(mockActor);
        given(mockActor.getCompanyId()).willReturn(anotherCompanyId);

        // when
        CustomException e = catchThrowableOfType(
                CustomException.class,
                () -> memberService.summaryCompanyMember(companyId, actorEmail)
        );

        // then
        assertThat(e.getErrorCode())
                .isEqualTo(ErrorCode.COMPANY_ACCESS_DENIED);
    }
}

package org.thisway.member.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thisway.member.domain.*;
import org.thisway.support.common.CustomException;
import org.thisway.support.common.ErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberReaderImplTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CompanyClient companyClient;

    @Mock
    private MemberInfoMapper memberInfoMapper;

    @InjectMocks
    private MemberReaderImpl memberReaderImpl;

    @Test
    @DisplayName("멤버ID로 멤버를 조회할 수 있다.")
    void 멤버ID로_멤버를_조회할_수_있다() {
        // given
        long memberId = 1L;
        Member mockMember = mock(Member.class);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(mockMember));

        // when
        Member result = memberReaderImpl.getMember(memberId);

        // then
        assertThat(result)
                .isSameAs(mockMember);
    }

    @Test
    @DisplayName("없는 멤버 ID 로 멤버 조회시 예외가 발생한다.")
    void 없는_멤버_ID_로_멤버_조회시_예외가_발생한다() {
        long memberId = 1L;
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        CustomException e = catchThrowableOfType(CustomException.class, () -> memberReaderImpl.getMember(memberId));

        assertThat(e.getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버Email로 멤버를 조회할 수 있다.")
    void 멤버Email로_멤버를_조회할_수_있다() {
        // given
        String memberEmail = "aaa@bbb.ccc";
        Member mockMember = mock(Member.class);

        when(memberRepository.findByEmail(memberEmail)).thenReturn(Optional.of(mockMember));

        // when
        Member result = memberReaderImpl.getMemberByEmail(memberEmail);

        // then
        assertThat(result)
                .isSameAs(mockMember);
    }

    @Test
    @DisplayName("없는 멤버 Email 로 멤버 조회시 예외가 발생한다.")
    void 없는_멤버_Email_로_멤버_조회시_예외가_발생한다() {
        String memberEmail = "aaa@bbb.ccc";
        when(memberRepository.findByEmail(memberEmail)).thenReturn(Optional.empty());

        CustomException e = catchThrowableOfType(CustomException.class, () -> memberReaderImpl.getMemberByEmail(memberEmail));

        assertThat(e.getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}

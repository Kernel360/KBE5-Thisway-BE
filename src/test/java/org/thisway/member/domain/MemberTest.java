package org.thisway.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @ParameterizedTest(name = "{0}은 {1}을 접근할 수 있다.")
    @MethodSource("접근_가능_멤버")
    @DisplayName("다른 멤버에 대한 접근 가능한 멤버를 판단할 수 있다.")
    void 다른_멤버에_대한_접근_가능한_멤버를_판단할_수_있다(Member actor, Member target) {
        assertThat(actor.canAccess(target)).isTrue();
    }

    @ParameterizedTest(name = "{0}은 {1}을 접근할 수 없다.")
    @MethodSource("접근_불가_멤버")
    @DisplayName("다른 멤버에 대한 접근 불가한 멤버를 판단할 수 있다.")
    void 다른_멤버에_대한_접근_불가한_멤버를_판단할_수_있다(Member actor, Member target) {
        assertThat(actor.canAccess(target)).isFalse();
    }

    private static Member createMember(long companyId, MemberRole role) {
        return Member.builder()
                .companyId(companyId)
                .phone("01012345678")
                .role(role)
                .build();
    }

    private static Stream<Arguments> 접근_가능_멤버() {
        return Stream.of(
                Arguments.of(createMember(1, MemberRole.ADMIN), createMember(2, MemberRole.COMPANY_CHEF)),
                Arguments.of(createMember(2, MemberRole.COMPANY_CHEF), createMember(2, MemberRole.MEMBER))
        );
    }

    private static Stream<Arguments> 접근_불가_멤버() {
        return Stream.of(
                Arguments.of(createMember(1, MemberRole.COMPANY_CHEF), createMember(2, MemberRole.MEMBER)),
                Arguments.of(createMember(2, MemberRole.MEMBER), createMember(2, MemberRole.COMPANY_CHEF))
        );
    }
}

package org.thisway.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MemberRoleTest {

    @ParameterizedTest(name = "{0}은 {1}을 접근할 수 있다.")
    @MethodSource("접근_가능_케이스")
    @DisplayName("역할 간 접근이 가능한 경우 true를 반환한다.")
    void 접근_가능_역할_테스트(MemberRole role, MemberRole target) {
        assertThat(role.canAccess(target)).isTrue();
    }

    @ParameterizedTest(name = "{0}은 {1}을 접근할 수 없다.")
    @MethodSource("접근_불가_케이스")
    @DisplayName("역할 간 접근이 불가한 경우 false 반환한다.")
    void 접근_불가_역할_테스트(MemberRole role, MemberRole target) {
        assertThat(role.canAccess(target)).isFalse();
    }

    @ParameterizedTest(name = "{0}의 접근 가능한 역할 {1}")
    @MethodSource("역할별_접근_권한")
    @DisplayName("역할별 접근 가능한 역할 목록을 반환한다.")
    void 역할별_접근_가능_역할_확인_테스트(MemberRole role, Set<MemberRole> expectedAccessibleRole) {
        Set<MemberRole> accessibleRoles = role.getAccessibleRoles();

        assertThat(accessibleRoles).containsExactlyInAnyOrderElementsOf(expectedAccessibleRole);
    }

    private static Stream<Arguments> 역할별_접근_권한() {
        return Stream.of(
                Arguments.of(MemberRole.ADMIN, Set.of(MemberRole.ADMIN, MemberRole.COMPANY_CHEF)),
                Arguments.of(MemberRole.COMPANY_ADMIN, Set.of(MemberRole.COMPANY_ADMIN, MemberRole.MEMBER))
        );
    }

    private static Stream<Arguments> 접근_가능_케이스() {
        return Stream.of(
                Arguments.of(MemberRole.ADMIN, MemberRole.COMPANY_CHEF),
                Arguments.of(MemberRole.COMPANY_CHEF, MemberRole.COMPANY_CHEF),
                Arguments.of(MemberRole.COMPANY_CHEF, MemberRole.MEMBER)
        );
    }

    private static Stream<Arguments> 접근_불가_케이스() {
        return Stream.of(
                Arguments.of(MemberRole.ADMIN, MemberRole.MEMBER),
                Arguments.of(MemberRole.COMPANY_CHEF, MemberRole.ADMIN)
        );
    }
}

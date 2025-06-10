package org.thisway.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.dto.request.AdminCompanyRegisterRequest;
import org.thisway.company.dto.response.AdminCompaniesResponse;
import org.thisway.company.dto.response.AdminCompanyResponse;
import org.thisway.company.entity.Company;
import org.thisway.company.repository.CompanyRepository;
import org.thisway.company.support.CompanyFixture;

class AdminCompanyServiceTest {

    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final AdminCompanyService adminCompanyService = new AdminCompanyService(companyRepository);

    @Test
    @DisplayName("업체 상세 정보를 조회할 수 있다.")
    void 업체_상세정보_조회_성공() {
        // given
        Company company = CompanyFixture.createCompany();
        when(companyRepository.findById(1L))
                .thenReturn(Optional.of(company));

        // when
        AdminCompanyResponse response = adminCompanyService.getCompanyDetail(1L);

        // then
        assertThat(response.id()).isEqualTo(company.getId());
        assertThat(response.name()).isEqualTo(company.getName());
        assertThat(response.crn()).isEqualTo(company.getCrn());
        assertThat(response.contact()).isEqualTo(company.getContact());
        assertThat(response.addrRoad()).isEqualTo(company.getAddrRoad());
        assertThat(response.addrDetail()).isEqualTo(company.getAddrDetail());
        assertThat(response.memo()).isEqualTo(company.getMemo());
        assertThat(response.gpsCycle()).isEqualTo(company.getGpsCycle());
    }

    @Test
    @DisplayName("존재하지 않거나 비활성화된 업체 조회 시 예외가 발생한다.")
    void 업체_상세정보_조회_실패() {
        // given
        Company inactiveCompany = CompanyFixture.createCompany();
        when(companyRepository.findById(inactiveCompany.getId()))
                .thenReturn(Optional.of(inactiveCompany));

        // when
        Throwable thrown = catchThrowable(() -> adminCompanyService.getCompanyDetail(2L));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException ex = (CustomException) thrown;
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("업체 리스트를 조회할 수 있다.")
    void 업체_리스트_조회_성공() {
        // given
        Company company = CompanyFixture.createCompany();
        PageImpl<Company> page = new PageImpl<>(List.of(company));
        when(companyRepository.findAllByActiveTrue(any()))
                .thenReturn(page);

        // when
        AdminCompaniesResponse result = adminCompanyService.getCompanies(PageRequest.of(0, 10));

        // then
        assertThat(result.adminCompanyRespons()).hasSize(1);
    }

    @Test
    @DisplayName("신규 업체 등록에 성공한다.")
    void 업체_등록_성공() {
        // given
        AdminCompanyRegisterRequest request = new AdminCompanyRegisterRequest(
                "company",
                "123456",
                "010-1234-5678",
                "road",
                "detail",
                "memo",
                60
        );
        when(companyRepository.existsByCrn("123456")).thenReturn(false);

        // when
        adminCompanyService.registerCompany(request);

        // then
        verify(companyRepository, times(1))
                .save(any(Company.class));
    }

    @Test
    @DisplayName("이미 존재하는 사업자등록번호로 등록 시 예외가 발생한다.")
    void 업체_등록_실패() {
        // given
        AdminCompanyRegisterRequest request = new AdminCompanyRegisterRequest(
                "company",
                "123456",
                "010-1234-5678",
                "road",
                "detail",
                "memo",
                60
        );
        when(companyRepository.existsByCrn("123456")).thenReturn(true);

        // when
        Throwable thrown = catchThrowable(() -> adminCompanyService.registerCompany(request));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException ex = (CustomException) thrown;
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMPANY_ALREADY_EXIST);
    }

    @Test
    @DisplayName("업체를 삭제할 수 있다.")
    void 업체_삭제_성공() {
        // given
        Company company = spy(CompanyFixture.createCompany());
        when(companyRepository.findByIdAndActiveTrue(anyLong()))
                .thenReturn(Optional.of(company));

        // when
        adminCompanyService.deleteCompany(1L);

        // then
        verify(company).delete();
    }

    @Test
    @DisplayName("존재하지 않는 업체 삭제 시 예외가 발생한다.")
    void 업체_삭제_실패() {
        // given
        when(companyRepository.findByIdAndActiveTrue(1L))
                .thenReturn(Optional.empty());

        // when & then
        Throwable thrown = catchThrowable(() -> adminCompanyService.deleteCompany(1L));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        CustomException ex = (CustomException) thrown;
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMPANY_NOT_FOUND);
    }
}

package org.thisway.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.thisway.auth.dto.VerificationPayload;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.company.entity.Company;
import org.thisway.company.support.CompanyFixture;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;
import org.thisway.member.support.MemberFixture;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
public class EmailVerificationServiceTest {

    @MockitoBean
    private JavaMailSender javaMailSender;
    private final StringRedisTemplate redisTemplate;

    private final EmailVerificationService emailVerificationService;
    @MockitoBean
    private final MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("생성된 코드가 redis에 저장된다.")
    void givenValidEmailAndVerifyCode_whenSendVerifyCode_thenStoreCodeInRedis() throws Exception {
        String email = "abc@example.com";
        String verifyCode = emailVerificationService.generateVerifyCode();
        VerificationPayload entry = new VerificationPayload(verifyCode, System.currentTimeMillis()+ 1000*60);

        emailVerificationService.storeCode(email, entry);

        String savedCode = redisTemplate.opsForValue().get(email);
        assertThat(savedCode).isNotNull();

        ObjectMapper objectMapper = new ObjectMapper();
        VerificationPayload savedEntry = objectMapper.readValue(savedCode, VerificationPayload.class);

        assertThat(savedEntry.code().length()).isEqualTo(6);
        assertThat(savedEntry.code()).isEqualTo(verifyCode);
    }

    @Test
    @DisplayName("메일 발송 과정에서 MailException 에러 발생 시, server_error 응답을 한다.")
    void whenSendVerifyCodeAndMailExceptionThrown_thenReturnServerErrorStatus() throws Exception {
        String email = "abc@example.com";
        String verifyCode = emailVerificationService.generateVerifyCode();

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        doThrow(new MailSendException("메일 전송 실패"))
                .when(javaMailSender).send(any(MimeMessage.class));

        CustomException e = assertThrows(CustomException.class, () -> emailVerificationService.sendMail(email, verifyCode));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR);
    }

    @Test
    @DisplayName("메일 발송 과정에서 MessagingException 에러 발생 시, server_error 응답을 한다.")
    void whenSendVerifyCodeAndMessagingExceptionThrown_thenReturnServerErrorStatus() throws Exception {
        String email = " ";
        String verifyCode = emailVerificationService.generateVerifyCode();

        CustomException e = assertThrows(CustomException.class, () -> emailVerificationService.sendMail(email, verifyCode));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR);
    }

    @Test
    @DisplayName("sendVerifyCode 실행 시 이메일이 존재하는지 확인하고, storeCode와 sendEmail을 호출한다.")
    void whenSendVerifyCode_thenCheckEmailExistAndCallStoreCodeAndSendEmail() throws Exception {
        Company company = CompanyFixture.createCompany();
        Member member = MemberFixture.createMember(company);

        EmailVerificationService emailVerificationServiceSpy = Mockito.spy(emailVerificationService);

        when(memberRepository.findByEmailAndActiveTrue(anyString())).thenReturn(Optional.of(member));
        doNothing().when(emailVerificationServiceSpy).storeCode(anyString(), any(VerificationPayload.class));
        doNothing().when(emailVerificationServiceSpy).sendMail(anyString(), anyString());

        emailVerificationServiceSpy.sendVerifyCode("hong@example.com");
        verify(emailVerificationServiceSpy).storeCode(anyString(), any(VerificationPayload.class));
        verify(emailVerificationServiceSpy).sendMail(anyString(), anyString());
    }
}

package org.thisway.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
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
import org.thisway.auth.dto.request.PasswordChangeRequest;
import org.thisway.auth.dto.request.SendVerifyCodeRequest;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.member.entity.Member;
import org.thisway.member.repository.MemberRepository;
import org.thisway.member.support.MemberFixture;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
public class EmailVerificationServiceTest {

    @MockitoBean
    private JavaMailSender javaMailSender;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final EmailVerificationService emailVerificationService;
    @MockitoBean
    private final MemberRepository memberRepository;

    @Test
    @DisplayName("생성된 코드가 redis에 저장된다.")
    void givenValidEmailAndVerifyCode_whenSendVerifyCode_thenStoreCodeInRedis() throws Exception {
        String email = "abc@example.com";
        String verifyCode = emailVerificationService.generateVerifyCode();
        VerificationPayload entry = new VerificationPayload(verifyCode, System.currentTimeMillis()+ 1000*60);

        emailVerificationService.storeCode(email, entry);

        String savedCode = redisTemplate.opsForValue().get(email);
        assertThat(savedCode).isNotNull();

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
        Member member = MemberFixture.createMember();

        EmailVerificationService emailVerificationServiceSpy = Mockito.spy(emailVerificationService);

        when(memberRepository.findByEmailAndActiveTrue(anyString())).thenReturn(Optional.of(member));
        doNothing().when(emailVerificationServiceSpy).storeCode(anyString(), any(VerificationPayload.class));
        doNothing().when(emailVerificationServiceSpy).sendMail(anyString(), anyString());

        SendVerifyCodeRequest request = new SendVerifyCodeRequest(member.getEmail());
        emailVerificationServiceSpy.sendVerifyCode(request);
        verify(emailVerificationServiceSpy).storeCode(anyString(), any(VerificationPayload.class));
        verify(emailVerificationServiceSpy).sendMail(anyString(), anyString());
    }

    @Test
    @DisplayName("verifyCode 실행 시 retrieveFromRedis를 호출한다.")
    void whenVerifyCode_thenCallRetrieveFromRedis() throws Exception {
        EmailVerificationService emailVerificationServiceSpy = Mockito.spy(emailVerificationService);

        VerificationPayload entry = new VerificationPayload("123456", System.currentTimeMillis() + 60000);
        doReturn(entry).when(emailVerificationServiceSpy).retrieveFromRedis(anyString());
        emailVerificationServiceSpy.verifyCode("hong@example.com", "123456");
        verify(emailVerificationServiceSpy).retrieveFromRedis(anyString());
    }

    @Test
    @DisplayName("인증 코드 검증 시 유효한 코드라면 True 반환")
    void givenValidCode_whenVerifyCode_thenReturnTrue() throws Exception {
        EmailVerificationService emailVerificationServiceSpy = Mockito.spy(emailVerificationService);

        VerificationPayload entry = new VerificationPayload("123456", System.currentTimeMillis() + 60000);
        doReturn(entry).when(emailVerificationServiceSpy).retrieveFromRedis(any(String.class));

        assertThat(emailVerificationServiceSpy.verifyCode("hong@example.com", "123456").booleanValue()).isTrue();
    }

    @Test
    @DisplayName("인증 코드 검증 시 잘못된 코드라면 False 반환")
    void givenWrongCode_whenVerifyCode_thenReturnFalse() throws Exception {
        EmailVerificationService emailVerificationServiceSpy = Mockito.spy(emailVerificationService);

        VerificationPayload entry = new VerificationPayload("123456", System.currentTimeMillis() + 60000);
        doReturn(entry).when(emailVerificationServiceSpy).retrieveFromRedis(any(String.class));

        assertThat(emailVerificationServiceSpy.verifyCode("hong@example.com", "654321").booleanValue()).isFalse();
    }

    @Test
    @DisplayName("인증 코드 검증 시 만료된 코드라면 False 반환")
    void givenExpiredCode_whenVerifyCode_thenReturnFalse() throws Exception {
        EmailVerificationService emailVerificationServiceSpy = Mockito.spy(emailVerificationService);

        VerificationPayload entry = new VerificationPayload("123456", System.currentTimeMillis() - 10000);
        doReturn(entry).when(emailVerificationServiceSpy).retrieveFromRedis(any(String.class));

        assertThat(emailVerificationServiceSpy.verifyCode("hong@example.com", "123456").booleanValue()).isFalse();
    }

    @Test
    @DisplayName("changePassword 실행 시 verifyCode를 호출한다.")
    void whenChangePassword_thenCallVerifyCode() throws Exception {
        Member member = MemberFixture.createMember();
        EmailVerificationService emailVerificationServiceSpy = Mockito.spy(emailVerificationService);

        doReturn(Optional.of(member)).when(memberRepository).findByEmailAndActiveTrue(anyString());
        doReturn(true).when(emailVerificationServiceSpy).verifyCode(anyString(), anyString());

        PasswordChangeRequest request = new PasswordChangeRequest(member.getEmail(), "123456", "theNewPassword");
        emailVerificationServiceSpy.changePassword(request);
        verify(emailVerificationServiceSpy).verifyCode(member.getEmail(), "123456");
    }

}

package com.school.app.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalExceptionHandlerTest {

    @Mock
    private MessageSource messageSource;

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(messageSource);
        request = new MockHttpServletRequest("GET", "/api/v1/students");
        lenient().when(messageSource.getMessage(eq("error.badCredentials"), isNull(), any(Locale.class)))
                .thenReturn("Invalid email or password");
        lenient().when(messageSource.getMessage(eq("error.accessDenied"), isNull(), any(Locale.class)))
                .thenReturn("Access denied");
        lenient().when(messageSource.getMessage(eq("error.validationFailed"), isNull(), any(Locale.class)))
                .thenReturn("Validation failed");
        lenient().when(messageSource.getMessage(eq("error.generic"), isNull(), any(Locale.class)))
                .thenReturn("Something went wrong");
    }

    @Test
    void resourceNotFoundMapsTo404WithTheExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new ResourceNotFoundException("Student not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Student not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/students");
        assertThat(response.getBody().code()).isNull();
    }

    @Test
    void badRequestMapsTo400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadRequest(new BadRequestException("bad input"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("bad input");
    }

    @Test
    void duplicateResourceMapsTo409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDuplicate(new DuplicateResourceException("already exists"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("already exists");
    }

    @Test
    void tooManyRequestsMapsTo429() {
        ResponseEntity<ErrorResponse> response =
                handler.handleTooManyRequests(new TooManyRequestsException("slow down"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().message()).isEqualTo("slow down");
    }

    @Test
    void notConfiguredMapsTo503() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotConfigured(new NotConfiguredException("SMS provider not configured"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().message()).isEqualTo("SMS provider not configured");
    }

    @Test
    void featureNotEntitledMapsTo403WithMachineReadableCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handleFeatureNotEntitled(new FeatureNotEntitledException("plan lacks MESSAGING"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("FEATURE_NOT_ENTITLED");
        assertThat(response.getBody().message()).isEqualTo("plan lacks MESSAGING");
    }

    @Test
    void limitExceededMapsTo409WithMachineReadableCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handleLimitExceeded(new LimitExceededException("at most 150"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("LIMIT_EXCEEDED");
    }

    @Test
    void badCredentialsMapsTo401WithLocalizedMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadCredentials(new BadCredentialsException("raw internal detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // The raw exception message must never leak — the localized message is used instead.
        assertThat(response.getBody().message()).isEqualTo("Invalid email or password");
    }

    @Test
    void disabledAccountIsIndistinguishableFromBadCredentials() {
        ResponseEntity<ErrorResponse> badCredentials =
                handler.handleBadCredentials(new BadCredentialsException("x"), request);
        ResponseEntity<ErrorResponse> disabled =
                handler.handleDisabled(new DisabledException("account disabled"), request);

        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(disabled.getBody().message()).isEqualTo(badCredentials.getBody().message());
    }

    @Test
    void accessDeniedMapsTo403() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("nope"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("Access denied");
    }

    @Test
    void validationErrorsMapTo400WithPerFieldMessages() throws NoSuchMethodException {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "name", "must not be blank"));
        binding.addError(new FieldError("request", "email", "must be a well-formed email address"));
        MethodParameter parameter = new MethodParameter(
                getClass().getDeclaredMethod("dummyMethod", String.class), 0);

        ResponseEntity<ErrorResponse> response =
                handler.handleValidation(new MethodArgumentNotValidException(parameter, binding), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().fieldErrors()).containsExactly(
                "name: must not be blank",
                "email: must be a well-formed email address");
    }

    @Test
    void unexpectedExceptionsMapTo500WithoutLeakingInternals() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new IllegalStateException("secret internal state"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Something went wrong");
        assertThat(response.getBody().message()).doesNotContain("secret internal state");
    }

    @SuppressWarnings("unused")
    private void dummyMethod(String argument) {
        // Exists only so a MethodParameter can be built for MethodArgumentNotValidException.
    }
}

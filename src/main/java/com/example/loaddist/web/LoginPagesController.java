package com.example.loaddist.web;

import com.example.loaddist.model.AppUser;
import com.example.loaddist.repository.AppUserRepository;
import com.example.loaddist.service.GraphMailService;
import com.example.loaddist.service.NotificationSettingsService;
import com.example.loaddist.service.PasswordResetService;
import com.example.loaddist.service.SentEmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class LoginPagesController {

    private final AppUserRepository appUserRepository;
    private final PasswordResetService passwordResetService;
    private final GraphMailService graphMailService;
    private final NotificationSettingsService notificationSettingsService;
    private final SentEmailService sentEmailService;
    private final PasswordEncoder passwordEncoder;
    private final String appBaseUrl;

    public LoginPagesController(AppUserRepository appUserRepository,
                                PasswordResetService passwordResetService,
                                GraphMailService graphMailService,
                                NotificationSettingsService notificationSettingsService,
                                SentEmailService sentEmailService,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.base-url:}") String appBaseUrl) {
        this.appUserRepository = appUserRepository;
        this.passwordResetService = passwordResetService;
        this.graphMailService = graphMailService;
        this.notificationSettingsService = notificationSettingsService;
        this.sentEmailService = sentEmailService;
        this.passwordEncoder = passwordEncoder;
        this.appBaseUrl = appBaseUrl != null ? appBaseUrl : "";
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String reset,
                        @RequestParam(required = false) String error,
                        @RequestParam(required = false) String next,
                        Authentication authentication,
                        Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("loginError", error != null);
        model.addAttribute("info", "1".equals(reset) ? "Password updated — sign in below." : null);
        model.addAttribute("nextPath", safeNext(next));
        return "login";
    }

    @GetMapping("/login/forgot-password")
    public String forgotGet(Authentication authentication, Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("error", null);
        model.addAttribute("info", null);
        model.addAttribute("configured", graphMailService.isConfigured(notificationSettingsService.get()));
        return "login-forgot-password";
    }

    @PostMapping("/login/forgot-password")
    public String forgotPost(@RequestParam String username,
                             Authentication authentication,
                             HttpServletRequest request,
                             Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        var settings = notificationSettingsService.get();
        boolean configured = graphMailService.isConfigured(settings);
        String genericDone =
                "If that username matches an employee account, we'll send reset instructions to that address shortly. Check spam if nothing arrives.";
        String mailMissing =
                "Self-service reset needs Microsoft Graph configured (Notifications). Ask an administrator to set it up or to reset your password.";
        String cannotSend = "We could not send the email — try again in a few minutes or ask an administrator.";

        String u = username != null ? username.trim() : "";
        if (u.isEmpty()) {
            model.addAttribute("error", "Enter your login username (email-style address).");
            model.addAttribute("info", null);
            model.addAttribute("configured", configured);
            return "login-forgot-password";
        }

        var userOpt = appUserRepository.findByUsernameIgnoreCase(u);
        if (!configured && userOpt.isPresent() && "employee".equalsIgnoreCase(userOpt.get().getRole())) {
            model.addAttribute("error", mailMissing);
            model.addAttribute("info", null);
            model.addAttribute("configured", false);
            return "login-forgot-password";
        }
        if (!configured) {
            model.addAttribute("error", null);
            model.addAttribute("info", genericDone);
            model.addAttribute("configured", false);
            return "login-forgot-password";
        }
        if (userOpt.isEmpty() || !"employee".equalsIgnoreCase(userOpt.get().getRole())) {
            model.addAttribute("error", null);
            model.addAttribute("info", genericDone);
            model.addAttribute("configured", true);
            return "login-forgot-password";
        }

        AppUser user = userOpt.get();
        PasswordResetService.IssueResult issued = passwordResetService.issueForEmployeeUser(user);
        if (issued.throttled() || issued.plainToken() == null || issued.tokenRowId() == null) {
            model.addAttribute("error", null);
            model.addAttribute("info", genericDone);
            model.addAttribute("configured", true);
            return "login-forgot-password";
        }

        String base = resolvePublicBase(request);
        String resetUrl = base + "/login/reset-password?token=" + URLEncoder.encode(issued.plainToken(), StandardCharsets.UTF_8);
        String subject = "Reset your Goals password";
        String text = String.join("\n", new String[]{
                "You requested a new password for Goals.",
                "",
                "Use this link within about one hour:",
                "",
                resetUrl,
                "",
                "If you did not request this, ignore this email.",
                ""
        });
        String safeUrl = HtmlUtils.htmlEscape(resetUrl);
        String html = "<p>You requested to reset your Goals password.</p><p><a href=\"" + safeUrl + "\">Choose a new password</a>.</p>"
                + "<p>This link expires in about one hour. If you didn't ask for this, you can ignore this message.</p>";
        try {
            graphMailService.sendMail(settings, user.getUsername(), subject, text, html);
            sentEmailService.append(user.getUsername(), subject, "password_reset", "forgot_password", "sent", null, "");
        } catch (Exception e) {
            if (issued.tokenRowId() != null) {
                passwordResetService.deleteById(issued.tokenRowId());
            }
            model.addAttribute("error", cannotSend);
            model.addAttribute("info", null);
            model.addAttribute("configured", true);
            return "login-forgot-password";
        }

        model.addAttribute("error", null);
        model.addAttribute("info", genericDone);
        model.addAttribute("configured", true);
        return "login-forgot-password";
    }

    @GetMapping("/login/reset-password")
    public String resetGet(@RequestParam(required = false) String token,
                           Authentication authentication,
                           Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        String t = token != null ? token.trim() : "";
        if (t.isEmpty()) {
            model.addAttribute("error", null);
            model.addAttribute("token", "");
            model.addAttribute("valid", false);
            return "login-reset-password";
        }
        if (!passwordResetService.tokenValid(t)) {
            model.addAttribute("error",
                    "This link is invalid or has expired. Use Forgot password below to receive a fresh link.");
            model.addAttribute("token", "");
            model.addAttribute("valid", false);
            return "login-reset-password";
        }
        model.addAttribute("error", null);
        model.addAttribute("token", t);
        model.addAttribute("valid", true);
        return "login-reset-password";
    }

    @PostMapping("/login/reset-password")
    public String resetPost(@RequestParam String token,
                            @RequestParam String password,
                            @RequestParam("password_confirm") String passwordConfirm,
                            Authentication authentication,
                            Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        String t = token != null ? token.trim() : "";
        if (t.isEmpty()) {
            model.addAttribute("error", "Missing reset token.");
            model.addAttribute("token", "");
            model.addAttribute("valid", false);
            return "login-reset-password";
        }
        if (!passwordResetService.tokenValid(t)) {
            model.addAttribute("error",
                    "This link is invalid or has expired. Use Forgot password to request another email.");
            model.addAttribute("token", "");
            model.addAttribute("valid", false);
            return "login-reset-password";
        }
        if (password == null || password.length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters.");
            model.addAttribute("token", t);
            model.addAttribute("valid", true);
            return "login-reset-password";
        }
        if (!password.equals(passwordConfirm)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("token", t);
            model.addAttribute("valid", true);
            return "login-reset-password";
        }
        try {
            passwordResetService.applyReset(t, password, passwordEncoder);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage() != null ? e.getMessage() : "Could not reset password.");
            model.addAttribute("token", "");
            model.addAttribute("valid", false);
            return "login-reset-password";
        }
        return "redirect:/login?reset=1";
    }

    private static boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static String safeNext(String next) {
        if (next == null) return "";
        String t = next.trim();
        if (t.startsWith("/") && !t.startsWith("//")) return t;
        return "";
    }

    private String resolvePublicBase(HttpServletRequest request) {
        if (!appBaseUrl.isBlank()) {
            return appBaseUrl.replaceAll("/+$", "");
        }
        return UriComponentsBuilder.fromHttpUrl(request.getRequestURL().toString())
                .replacePath(request.getContextPath())
                .replaceQuery(null)
                .fragment(null)
                .build()
                .toUriString();
    }
}

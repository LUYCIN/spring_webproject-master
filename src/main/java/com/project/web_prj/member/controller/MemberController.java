package com.project.web_prj.member.controller;

import com.project.web_prj.member.domain.Member;
import com.project.web_prj.member.domain.OAuthValue;
import com.project.web_prj.member.domain.SNSLogin;
import com.project.web_prj.member.dto.LoginDTO;
import com.project.web_prj.member.service.KakaoService;
import com.project.web_prj.member.service.LoginFlag;
import com.project.web_prj.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static com.project.web_prj.member.domain.OAuthValue.*;
import static com.project.web_prj.util.LoginUtils.*;

@Controller
@Log4j2
@RequiredArgsConstructor
@RequestMapping("/member")
public class MemberController {

    private final MemberService memberService;
    private final KakaoService kakaoService;

    // 회원가입 양식 띄우기 요청
    @GetMapping("/sign-up")
    public void signUp() {
        log.info("/member/sign-up GET! - forwarding to sign-up.jsp");
    }

    // 회원가입 처리 요청
    @PostMapping("/sign-up")
    public String signUp(Member member, RedirectAttributes ra) {
        log.info("/member/sign-up POST ! - {}", member);
        boolean flag = memberService.signUp(member);
        ra.addFlashAttribute("msg", "reg-success");
        return flag ? "redirect:/member/sign-in" : "redirect:/member/sign-up";
    }

    // 아이디, 이메일 중복확인 비동기 요청 처리
    @GetMapping("/check")
    @ResponseBody
    public ResponseEntity<Boolean> check(String type, String value) {
        log.info("/member/check?type={}&value={} GET!! ASYNC", type, value);
        boolean flag = memberService.checkSignUpValue(type, value);

        return new ResponseEntity<>(flag, HttpStatus.OK);
    }

    // 로그인 화면을 열어주는 요청처리
    @GetMapping("/sign-in")
    public void signIn(@ModelAttribute("message") String message, HttpServletRequest request, Model model) {
        log.info("/member/sign-in GET! - forwarding to sign-in.jsp - {}", message);

        // 요청 정보 헤더 안에는 Referer라는 키가 있는데
        // 여기 안에는 이 페이지로 진입할 때 어디에서 왔는지 URI정보가 들어있음.
        String referer = request.getHeader("Referer");
        log.info("referer: {}", referer);

        request.getSession().setAttribute("redirectURI", referer);

        model.addAttribute("kakaoAppKey", KAKAO_APP_KEY);
        model.addAttribute("kakaoRedirect", KAKAO_REDIRECT_URI);
    }

    // 로그인 요청 처리
    @PostMapping("/sign-in")     // LoginDTO 대신 Member로 받아도 되지만, Member로 받게되면 너무 과하다. (안넘어도는 아이디도 있는데 모두 받아오게 된다)
    public String signIn(LoginDTO inputData
            , Model model
            , HttpSession session // 세션정보 객체
            , HttpServletResponse response
    ) {

        log.info("/member/sign-in POST - {}", inputData);
//        log.info("session timeout : {}", session.getMaxInactiveInterval());

        // 로그인 서비스 호출
        LoginFlag flag = memberService.login(inputData, session, response);

        if (flag == LoginFlag.SUCCESS) {
            log.info("login success!!");
            String redirectURI = (String) session.getAttribute("redirectURI");
            return "redirect:" + redirectURI;  // 세션에서 얻어온 정보를 가지고 그곳으로 redirect를 한다.   redirectURI 에는 referer가 있다.
        }
        
        model.addAttribute("loginMsg", flag);    // sign-in.jsp 113번에서 정의한다
        return "member/sign-in";

        // 브라우저 하나당 세션이 한개이므로 브라우져를 종료시키면 세션도 종료가 된다
    }

    @GetMapping("/sign-out")   // 로그아웃
    public String signOut(HttpServletRequest request, HttpServletResponse response) throws Exception {

        HttpSession session = request.getSession();

        if (isLogin(session)) {

            // 만약 자동로그인 상태라면 해제한다.
            if (hasAutoLoginCookie(request)) {   // 자동 로그인 유무를 알 수 있게 쿠키를 알아본다
                memberService.autoLogout(getCurrentMemberAccount(session), request, response);
            }

            // SNS로그인 상태라면 해당 SNS 로그아웃처리를 진행
            SNSLogin from = (SNSLogin) session.getAttribute(LOGIN_FROM);

            if (from != null) {  // 로그인이 되어있으면 즉, null이 아니면
                switch (from) {
                    case KAKAO:
                        kakaoService.logout((String) session.getAttribute("accessToken"));
                        break;
                    case NAVER:
                        break;
                    case GOOGLE:
                        break;
                    case FACEBOOK:
                        break;
                }
            }

            // 1. 세션에서 정보를 삭제한다.
            session.removeAttribute(LOGIN_FLAG);

            // 2. 세션을 무효화한다.
            session.invalidate();
            return "redirect:/";
        }
        return "redirect:/member/sign-in";
    }
}

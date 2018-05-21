package com.tcc.web.controller;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.EncodeException;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.tcc.common.utils.Md5Util;
import com.tcc.common.utils.VerificationUtil;
import com.tcc.web.entity.User;
import com.tcc.web.websocket.MsMsg;
import com.tcc.web.websocket.MsWebSocket;

@Controller
public class LoginController {
	
	private final Logger logger = LoggerFactory.getLogger(LoginController.class);
	
	@Resource
    private RedisTemplate<String, Object> redisTemplate;
	
	@PostMapping("/login")
	@ResponseBody
	public String login(User user, String uri, String verification) throws IOException, EncodeException, NoSuchAlgorithmException {
		String result = "登录成功";
		ValueOperations<String, Object> voper = redisTemplate.opsForValue();
		@SuppressWarnings("unchecked")
		HashMap<String, Object> map = (HashMap<String, Object>)voper.get(uri);
		if(map == null){
			result = "无效的用户标识";
			MsWebSocket.send(uri, MsMsg.msg(result, "login", "recreateUri"));
		}
		else if(map.get("verification") == null){
			result = "验证码过期";
			MsWebSocket.send(uri, MsMsg.msg(result, "login", "reflashVerification"));
		}
		else if(!Md5Util.b64Md5(verification.toLowerCase(), "verification").equals(map.get("verification"))){
			result = "验证码不正确";
			MsWebSocket.send(uri, MsMsg.msg(result, "login"));
		}
		else {
			Subject subject = SecurityUtils.getSubject();
			UsernamePasswordToken token = new UsernamePasswordToken(user.getUsername(), user.getPassword());
			try {  
		        subject.login(token);
			}
			catch(UnknownAccountException e){
				result = "用户名或密码不正确";
			}
			catch(IncorrectCredentialsException e){
				result = "用户名或密码不正确";
			}
			catch(LockedAccountException e){
				result = "您的账户已被锁定，请30分钟后重新尝试";
			}
			catch(DisabledAccountException e){
				result = "您的账户已被禁用，请联系管理员";
			}
			finally {
				MsWebSocket.send(uri, MsMsg.msg(result, "login", "logon"));
			}
		}
		return result;
	}
	
	@RequestMapping("/login")
	public String login() {
		return "login";
	}
	
	@RequestMapping("logon")
	public String logon(){
		return "logon";
	}
	
	@RequestMapping("/register")
	public String register() {
		return "register";
	}
	
	@RequestMapping("/verification")
	public void verification(@RequestParam(value="uri", required = true)String uri, HttpServletResponse response) throws IOException, EncodeException, NoSuchAlgorithmException {
		ValueOperations<String, Object> voper = redisTemplate.opsForValue();
		@SuppressWarnings("unchecked")
		HashMap<String, Object> map = (HashMap<String, Object>)voper.get(uri);
		if(map == null){
			MsWebSocket.send(uri, MsMsg.msg("无效的用户标识", "verification","recreateUri"));
		}
		else{
			response.setContentType("image/jpeg");
			com.tcc.common.utils.VerificationUtil.Result result = VerificationUtil.create();
			String encryptionCode = Md5Util.b64Md5(result.getCode().toLowerCase(), "verification");
			logger.info("generate verification {} for {}, encryptionCode is {}",result.getCode(), uri, encryptionCode);
			map.put("verification", encryptionCode);
			voper.set(uri, map);
			//redisTemplate.expire(uri, 30, TimeUnit.SECONDS);
			MsWebSocket.send(uri, MsMsg.msg(encryptionCode, "verification"));
			ImageIO.write(result.getImage(), "JPEG", response.getOutputStream());
		}
	}
}

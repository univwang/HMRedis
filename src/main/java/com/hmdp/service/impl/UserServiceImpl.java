package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Random;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final String defaultIcon = "https://assets.leetcode.cn/aliyun-lc-upload/users/wang-gan-yu/avatar_1662429739.png?x-oss-process=image%2Fformat%2Cwebp";

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码
        session.setAttribute("code", code);
        //4.发送验证码
        log.debug("发送验证码：{}", code);
        //5.返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式不正确");
        }
        //2.校验验证码
        Object code = session.getAttribute("code");
        //3.不一致返回错误信息
        if (code == null || !code.toString().equals(loginForm.getCode())) {
            return Result.fail("验证码不正确");
        }
        //4.一致则查询用户信息
        User user = query().eq("phone", loginForm.getPhone()).one();
        //5.用户不存在则创建用户
        if (user == null) {
            user = createUserWithPhone(loginForm.getPhone());
        }
        //6.保存用户信息到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("用户" + RandomUtil.randomNumbers(6));
        user.setIcon(defaultIcon);
        save(user);
        return user;
    }
}

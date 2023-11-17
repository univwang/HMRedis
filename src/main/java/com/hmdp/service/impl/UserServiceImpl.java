package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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
    @Resource
    private StringRedisTemplate stringredisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码
//        session.setAttribute("code", code);
        stringredisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
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
        //2.从redis获取验证码进行校验
        String code = stringredisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        //3.不一致返回错误信息
        if (code == null || !code.equals(loginForm.getCode())) {
            return Result.fail("验证码不正确");
        }
        //4.一致则查询用户信息
        User user = query().eq("phone", loginForm.getPhone()).one();
        //5.用户不存在则创建用户
        if (user == null) {
            user = createUserWithPhone(loginForm.getPhone());
        }
        //6.保存用户信息到redis中
        //6.1 生成token
        String uuid = UUID.randomUUID().toString().replace("-", "");
        //6.2 将User对象转换为Hash
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //6.3 保存到redis中
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((field, value) -> {
                    return value.toString();
                }));
        stringredisTemplate.opsForHash().putAll(LOGIN_USER_KEY + uuid, userMap);
        //6.4 设置过期时间
        stringredisTemplate.expire(LOGIN_USER_KEY + uuid, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //7.返回token给客户端
        return Result.ok(uuid);
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

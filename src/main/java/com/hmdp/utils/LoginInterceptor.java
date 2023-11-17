package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // TODO 实现登录拦截器
        //1.获取session
        HttpSession session = request.getSession();
        //2.判断session中是否有用户信息
        UserDTO user = (UserDTO) session.getAttribute("user");
        //3.如果有，放行
        if(user == null){
            response.setStatus(401);
            return false;
        }
        //4.保存在ThreadLocal中

        UserHolder.saveUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    }
}

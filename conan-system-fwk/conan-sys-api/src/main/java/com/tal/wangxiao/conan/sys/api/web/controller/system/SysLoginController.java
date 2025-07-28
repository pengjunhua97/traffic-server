package com.tal.wangxiao.conan.sys.api.web.controller.system;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tal.wangxiao.conan.sys.auth.core.domain.model.LoginUser;
import com.tal.wangxiao.conan.sys.common.core.domain.entity.SysUser;
import com.tal.wangxiao.conan.sys.common.utils.ServletUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.tal.wangxiao.conan.sys.common.constant.Constants;
import com.tal.wangxiao.conan.sys.common.core.domain.AjaxResult;
import com.tal.wangxiao.conan.sys.common.core.domain.entity.SysMenu;
import com.tal.wangxiao.conan.sys.common.core.domain.model.LoginBody;
import com.tal.wangxiao.conan.sys.framework.web.service.SysLoginService;
import com.tal.wangxiao.conan.sys.framework.web.service.SysPermissionService;
import com.tal.wangxiao.conan.sys.framework.web.service.TokenService;
import com.tal.wangxiao.conan.sys.system.service.ISysMenuService;

/**
 * 登录验证
 *
 * @author ruoyi
 */
@RestController
public class SysLoginController {
    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private TokenService tokenService;

    /**
     * 登录方法
     *
     * @param loginBody 登录信息
     * @return 结果
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody) {
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

    @PostMapping("/auth/login")
    public Map<String,Object> authLogin(@RequestBody LoginBody loginBody) {
        Map<String,Object> map = new HashMap<>();
        // 生成令牌
        String token = loginService.authLogin(loginBody.getUsername(), loginBody.getPassword());
        Map<String,Object> data = new HashMap<>();
        data.put("token", token);
        map.put("data", data);
        map.put("code", 200);
        map.put("msg", "请求成功");
        return map;
    }

    /**
     * 获取用户信息
     *
     * @return 用户信息
     */
    @GetMapping("getInfo")
    public AjaxResult getInfo() {
        LoginUser loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        SysUser user = loginUser.getUser();
        // 角色集合
        Set<String> roles = permissionService.getRolePermission(user);
        // 权限集合
        Set<String> permissions = permissionService.getMenuPermission(user);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        ajax.put("roles", roles);
        ajax.put("permissions", permissions);
        return ajax;
    }

    @GetMapping("/auth/getUserInfo")
    public Map<String,Object> getUserInfo() {
        Map<String,Object> map = new HashMap<>();
        LoginUser loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        SysUser user = loginUser.getUser();
        // 角色集合
        Set<String> roles = permissionService.getRolePermission(user);
        // 权限集合
        Set<String> permissions = permissionService.getMenuPermission(user);
        Map<String,Object> data = new HashMap<>();
        data.put("id", user.getUserId());
        data.put("username", user.getUserName());
        data.put("nickname", user.getNickName());
        data.put("roles", roles);
        data.put("permissions", permissions);
        map.put("data", data);
        map.put("code", 200);
        map.put("msg", "请求成功");
        return map;
    }

    /**
     * 获取路由信息
     *
     * @return 路由信息
     */
    @GetMapping("getRouters")
    public AjaxResult getRouters() {
        LoginUser loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        // 用户信息
        SysUser user = loginUser.getUser();
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(user.getUserId());
        return AjaxResult.success(menuService.buildMenus(menus));
    }
}

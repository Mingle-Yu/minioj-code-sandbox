package com.github.mingleyu.miniojcodesandbox.security;

import java.security.Permission;

public class DefaultSecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission permission) {
        System.out.println("不做任务权限校验");
        System.out.println(permission);
//        super.checkPermission(permission);
    }
}

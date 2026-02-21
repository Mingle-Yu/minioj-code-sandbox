package com.github.mingleyu.miniojcodesandbox.security;

import java.security.Permission;

public class DenySecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission permission) {
        throw new SecurityException("权限异常 " + permission.toString());
    }
}

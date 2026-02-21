package com.github.mingleyu.miniojcodesandbox.codesandbox;


import com.github.mingleyu.miniojcodesandbox.model.ExecuteCodeRequest;
import com.github.mingleyu.miniojcodesandbox.model.ExecuteCodeResponse;

public interface CodeSandbox {
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}

package com.github.mingleyu.miniojcodesandbox.utils;

import com.github.mingleyu.miniojcodesandbox.model.ExecuteMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 进程工具类
 */
public class ProcessUtils {

    /**
     * 执行进程并获取信息
     *
     * @param process
     * @param opName
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process process, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            int exitValue = process.waitFor();
            executeMessage.setExitValue(exitValue);
            if (exitValue == 0) {
                System.out.println(opName + "成功");
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                String opOutputLine;
                List<String> outputStrList = new ArrayList<>();
                while ((opOutputLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(opOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList, "\n"));
            } else {
                System.out.println(opName + "失败，错误码 " + exitValue);
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                String opOutputLine;
                List<String> outputStrList = new ArrayList<>();
                while ((opOutputLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(opOutputLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList, "\n"));
                // 分批获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
                String errorOpOutputLine;
                List<String> errorOutputStrList = new ArrayList<>();
                while ((errorOpOutputLine = errorBufferedReader.readLine()) != null) {
                    errorOutputStrList.add(errorOpOutputLine);
                }
                executeMessage.setErrorMessage(StringUtils.join(errorOutputStrList, "\n"));
            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getTotalTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return executeMessage;
    }

    /**
     * 执行进程并获取信息
     *
     * @param process
     * @param args
     * @return
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process process, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            OutputStream outputStream = process.getOutputStream();

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            outputStreamWriter.write(args + "\n");
            outputStreamWriter.flush();

            InputStream inputStream = process.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder opOutputStringBuilder = new StringBuilder();
            String opOutputLine;
            while ((opOutputLine = bufferedReader.readLine()) != null) {
                opOutputStringBuilder.append(opOutputLine);
            }
            executeMessage.setMessage(opOutputStringBuilder.toString());
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getTotalTimeMillis());
            // 资源回收
            outputStreamWriter.close();
            outputStream.close();
            inputStream.close();
            process.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return executeMessage;
    }
}

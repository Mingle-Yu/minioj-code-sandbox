package com.github.mingleyu.miniojcodesandbox.codesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.mingleyu.miniojcodesandbox.model.ExecuteCodeRequest;
import com.github.mingleyu.miniojcodesandbox.model.ExecuteCodeResponse;
import com.github.mingleyu.miniojcodesandbox.model.ExecuteMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final long TIME_OUT = 10000L;

//    public static final Boolean FIRST_INIT = true;

    public static void main(String[] args) {
        JavaDockerCodeSandbox javaDockerCodeSandbox = new JavaDockerCodeSandbox();

        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
//        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        System.out.println(executeCodeRequest);

        ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);

    }

    @Override
    public List<ExecuteMessage> executeCodeFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        // 配置 Docker 客户端连接信息
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerTlsVerify(false)
                .build();

        // 配置 HttpClient
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .build();

        // 构建 DockerClient
        DockerClient dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();

        String containerId = null;

        try {
            // 拉取镜像
            String image = "openjdk:8u111-alpine";
            try {
                PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                    @Override
                    public void onNext(PullResponseItem item) {
                        System.out.println("下载镜像：" + item.getStatus());
                        super.onNext(item);
                    }
                };
                dockerClient.pullImageCmd(image).exec(pullImageResultCallback).awaitCompletion();
                System.out.println("下载完成");
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }

            // 创建容器
            HostConfig hostConfig = new HostConfig();
            hostConfig.withMemory(64 * 1024 * 1024L);
            hostConfig.withCpuCount(1L);
            hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));

            CreateContainerResponse createContainerResponse = dockerClient.createContainerCmd(image)
                    .withHostConfig(hostConfig)
                    .withNetworkDisabled(true)
                    .withReadonlyRootfs(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .withTty(false)
                    .withStdinOpen(true)
                    .withCmd("sh", "-c", "while true; do sleep 1; done")
                    .exec();

            System.out.println("创建容器：" + createContainerResponse);
            containerId = createContainerResponse.getId();

            // 启动容器
            dockerClient.startContainerCmd(containerId).exec();

            // 等待容器完全启动
            Thread.sleep(2000);

            List<ExecuteMessage> executeMessageList = new ArrayList<>();

            // 处理每个输入
            for (int i = 0; i < inputList.size(); i++) {
                String inputArgs = inputList.get(i);
                StopWatch stopWatch = new StopWatch();

                ExecuteMessage executeMessage = new ExecuteMessage();
                final StringBuilder messageBuilder = new StringBuilder();
                final StringBuilder errorMessageBuilder = new StringBuilder();

                // 为每个输入创建统计命令
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                final long[] maxMemory = {0L};
                final boolean[] statsStarted = {false};

                // 设置统计回调
                int finalI = i;
                ResultCallback<Statistics> statisticsResultCallback = new ResultCallback<Statistics>() {
                    private Closeable closeable;

                    @Override
                    public void onNext(Statistics statistics) {
                        try {
                            Long usage = statistics.getMemoryStats().getUsage();
                            if (usage != null && usage > 0) {
                                statsStarted[0] = true;
                                if (usage > maxMemory[0]) {
                                    maxMemory[0] = usage;
                                    System.out.println("当前内存占用：" + usage + " bytes (" + (usage/1024) + " KB)");
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("处理统计信息时出错：" + e.getMessage());
                        }
                    }

                    @Override
                    public void onStart(Closeable closeable) {
                        this.closeable = closeable;
                        System.out.println("统计命令启动 - 输入 " + finalI);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        System.err.println("统计命令错误：" + throwable.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        System.out.println("统计命令完成 - 输入 " + finalI);
                    }

                    @Override
                    public void close() throws IOException {
                        if (closeable != null) {
                            closeable.close();
                        }
                    }
                };

                // 执行统计命令
                statsCmd.exec(statisticsResultCallback);

                try {
                    // 等待统计命令启动
                    Thread.sleep(500);

                    // 使用 echo 通过管道传递输入
                    String[] cmdArray = {"sh", "-c", "echo \"" + inputArgs + "\" | java -cp /app Main"};

                    // 创建执行命令
                    ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                            .withCmd(cmdArray)
                            .withAttachStderr(true)
                            .withAttachStdout(true)
                            .withTty(false)
                            .exec();

                    System.out.println("创建执行命令：" + execCreateCmdResponse);

                    String execId = execCreateCmdResponse.getId();

                    // 创建执行回调
                    ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                        @Override
                        public void onNext(Frame frame) {
                            StreamType streamType = frame.getStreamType();
                            String content = new String(frame.getPayload(), StandardCharsets.UTF_8).trim();

                            if (!content.isEmpty()) {
                                if (StreamType.STDERR.equals(streamType)) {
                                    if (errorMessageBuilder.length() > 0) {
                                        errorMessageBuilder.append("\n");
                                    }
                                    errorMessageBuilder.append(content);
                                    System.out.println("输出错误结果：" + content);
                                } else {
                                    if (messageBuilder.length() > 0) {
                                        messageBuilder.append("\n");
                                    }
                                    messageBuilder.append(content);
                                    System.out.println("输出结果：" + content);
                                }
                            }
                            super.onNext(frame);
                        }
                    };

                    stopWatch.start();

                    // 执行命令
                    boolean completed = dockerClient.execStartCmd(execId)
                            .exec(execStartResultCallback)
                            .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);

                    stopWatch.stop();

                    // 等待一下确保最后的统计信息被捕获
                    Thread.sleep(500);

                    if (!completed) {
                        executeMessage.setErrorMessage("程序执行超时");
                        executeMessage.setTime(TIME_OUT);
                    } else {
                        // 设置执行结果
                        executeMessage.setMessage(messageBuilder.toString());
                        executeMessage.setErrorMessage(errorMessageBuilder.toString());
                        executeMessage.setTime(stopWatch.getTotalTimeMillis());

                        // 设置内存占用（转换为KB）
                        long memoryKB = maxMemory[0] / 1024;
                        executeMessage.setMemory(memoryKB == 0 ? 1 : memoryKB);
                        System.out.println("输入 " + i + " 最大内存占用: " + executeMessage.getMemory() + " KB");
                    }

                } catch (InterruptedException e) {
                    System.out.println("程序执行异常");
                    executeMessage.setErrorMessage("程序执行异常：" + e.getMessage());
                    executeMessage.setTime(TIME_OUT);
                    executeMessage.setMemory(0L);
                } finally {
                    // 关闭统计命令
                    if (statsCmd != null) {
                        statsCmd.close();
                    }
                }

                executeMessageList.add(executeMessage);
            }

            return executeMessageList;

        } catch (Exception e) {
            System.err.println("执行过程发生异常：" + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            // 清理容器资源
            if (containerId != null) {
                try {
                    dockerClient.stopContainerCmd(containerId).exec();
                    dockerClient.removeContainerCmd(containerId).exec();
                    System.out.println("容器已清理");
                } catch (Exception e) {
                    System.err.println("清理容器失败：" + e.getMessage());
                }
            }

            // 关闭 Docker 客户端
            try {
                dockerClient.close();
            } catch (Exception e) {
                System.err.println("关闭 Docker 客户端失败：" + e.getMessage());
            }
        }
    }

//    @Override
//    public List<ExecuteMessage> executeCodeFile(File userCodeFile, List<String> inputList) {
//        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
//        // 配置 Docker 客户端连接信息
//        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
//                .withDockerTlsVerify(false)
//                .build();
//
//        // 配置 HttpClient
//        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
//                .dockerHost(config.getDockerHost())
//                .sslConfig(config.getSSLConfig())
//                .maxConnections(100)
//                .build();
//
//        // 构建 DockerClient
//        DockerClient dockerClient = DockerClientBuilder.getInstance(config)
//                .withDockerHttpClient(httpClient)
//                .build();
//
//        // 拉取镜像
//        String image = "openjdk:8u111-alpine";
//        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
//        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
//            @Override
//            public void onNext(PullResponseItem item) {
//                System.out.println("下载镜像：" + item.getStatus());
//                super.onNext(item);
//            }
//        };
//        try {
//            pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
//        } catch (InterruptedException e) {
//            System.out.println("拉取镜像异常");
//            throw new RuntimeException(e);
//        }
//        System.out.println("下载完成");
//
//        // 创建容器
//        CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(image);
//        HostConfig hostConfig = new HostConfig();
//        hostConfig.withMemory(64 * 1024 * 1024L);
//        hostConfig.withCpuCount(1L);
//        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
//
//        CreateContainerResponse createContainerResponse = createContainerCmd
//                .withHostConfig(hostConfig)
//                .withNetworkDisabled(true)
//                .withReadonlyRootfs(true)
//                .withAttachStderr(true)
//                .withAttachStdin(true)
//                .withAttachStdout(true)
////                .withTty(true)
//                .withTty(false)
//                .withStdinOpen(true)
//                .withCmd("sh", "-c", "tail -f /dev/null")
////                .withCmd("sh", "-c", "while true; do sleep 1; done")
//                .exec();
//        System.out.println(createContainerResponse);
//        String containerId = createContainerResponse.getId();
//
//        // 启动容器
//        dockerClient.startContainerCmd(containerId).exec();
//
//        // 执行代码，并获取输出
//        List<ExecuteMessage> executeMessageList = new ArrayList<>();
//        for (String inputArgs : inputList) {
//            StopWatch stopWatch = new StopWatch();
////            String[] inputArgsArray = inputArgs.split(" ");
////            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
//            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"});
//            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
//                    .withCmd(cmdArray)
//                    .withAttachStderr(true)
//                    .withAttachStdin(true)
//                    .withAttachStdout(true)
//                    .withTty(false)
//                    .exec();
//            System.out.println("创建执行命令：" + execCreateCmdResponse);
//
//            ExecuteMessage executeMessage = new ExecuteMessage();
//            final String[] message = {null};
//            final String[] errorMessage = {null};
//            long time = 0;
//            final boolean[] timeOut = {true};
//            String execId = execCreateCmdResponse.getId();
//            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
//                @Override
//                public void onComplete() {
//                    timeOut[0] = false;
//                    super.onComplete();
//                }
//
//                @Override
//                public void onNext(Frame frame) {
//                    StreamType streamType = frame.getStreamType();
//                    String content = new String(frame.getPayload());
//                    content = content.trim();
//                    if (StreamType.STDERR.equals(streamType)) {
//                        errorMessage[0] = content;
//                        System.out.println("输出错误结果：" + errorMessage[0]);
//                    } else {
//                        message[0] = content;
//                        System.out.println("输出结果：" + message[0]);
//                    }
//                    super.onNext(frame);
//                }
//            };
//
//            // 获取占用的内存
//            final long[] maxMemory = {0L};
//            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
//            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
//
//                @Override
//                public void onNext(Statistics statistics) {
//                    System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
//                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
//                }
//
//                @Override
//                public void close() throws IOException {
//
//                }
//
//                @Override
//                public void onStart(Closeable closeable) {
//
//                }
//
//                @Override
//                public void onError(Throwable throwable) {
//
//                }
//
//                @Override
//                public void onComplete() {
//
//                }
//            });
//            statsCmd.exec(statisticsResultCallback);
//            String inputData = inputArgs + '\n';
//            InputStream inputStream  = new ByteArrayInputStream(inputData.getBytes(StandardCharsets.UTF_8));
//            try {
//                stopWatch.start();
//                dockerClient.execStartCmd(execId)
//                        .withStdIn(inputStream)
//                        .exec(execStartResultCallback)
//                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
//                stopWatch.stop();
//                time = stopWatch.getTotalTimeMillis();
//                statsCmd.close();
//            } catch (InterruptedException e) {
//                System.out.println("程序执行异常");
//                throw new RuntimeException(e);
//            }
//            executeMessage.setMessage(message[0]);
//            executeMessage.setErrorMessage(errorMessage[0]);
//            executeMessage.setTime(time);
//            executeMessage.setMemory(maxMemory[0] / 1024 == 0 ? 1 : maxMemory[0] / 1024); // 单位KB
//            executeMessageList.add(executeMessage);
//        }
//        return executeMessageList;
//    }
}
package com.github.mingleyu.miniojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.time.Duration;
import java.util.List;

public class DockerDemo {
    public static void main(String[] args) throws InterruptedException {
        // 1. 配置 Docker 客户端连接信息（WSL + Docker Desktop 的默认 TCP 地址）
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
//                .withDockerHost("tcp://localhost:2375") // Docker Desktop 暴露的 TCP 端口（需确认开启）
                .withDockerTlsVerify(false) // 本地开发关闭 TLS 验证
                .build();

        // 2. 配置 HttpClient（解决 WARN 提示，替代默认的 Jersey）
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
//                .connectionTimeout(Duration.ofSeconds(30))
//                .responseTimeout(Duration.ofSeconds(45))
                .build();

        // 3. 构建 DockerClient
        DockerClient dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();


        // 4. 执行 Docker 命令
        // 测试连接
//        dockerClient.pingCmd().exec();
//        System.out.println("连接成功");

        // 测试镜像拉取
        String image = "nginx:latest";
//        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
//        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
//            @Override
//            public void onNext(PullResponseItem item) {
//                System.out.println("下载镜像：" + item.getStatus());
//                super.onNext(item);
//            }
//        };
//        pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
//        System.out.println("下载完成");

        // 创建容器
//        CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(image);
//        CreateContainerResponse createContainerResponse = createContainerCmd.withCmd("echo", "Hello Docker").exec();
//        System.out.println(createContainerResponse);

        // 查看容器状态
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> containerList = listContainersCmd.withShowAll(true).exec();
        for (Container container : containerList) {
            System.out.println(container);
        }

        // 启动容器
        dockerClient.startContainerCmd(containerList.get(0).getId()).exec();

        // 查看日志
        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item) {
                System.out.println("日志：" + new String(item.getPayload()));
                super.onNext(item);
            }
        };
        dockerClient.logContainerCmd(containerList.get(0).getId())
                .withStdOut(true)
                .exec(logContainerResultCallback)
                .awaitCompletion();

        // 删除容器
        dockerClient.removeContainerCmd(containerList.get(0).getId()).exec();

        // 删除镜像
        dockerClient.removeImageCmd(image).exec();
    }
}
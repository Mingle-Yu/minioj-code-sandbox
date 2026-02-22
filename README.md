# minioj后端部署

远程服务器为Ubuntu24

### 本地项目打包

```shell
cd <项目根目录>
mvn clean package -DskipTests
```



### 上传文件到远程服务器

将打包好的文件`jar`包上传到`/home/ubuntu`目录



### 启动项目

```shell
sudo apt install screen -y
screen -S app
java -jar minioj-backend-0.0.1-SNAPSHOT.jar
# 按Ctrl+A+D脱离会话
# 重新连接会话：screen -r app
```
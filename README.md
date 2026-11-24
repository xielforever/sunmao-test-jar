# sunmao-test-jar

用于 SunMao CI/CD 发布测试的 Java 21 / Maven JAR 后端测试项目。

## 接口

- `GET /healthz`：健康检查，返回 `200 OK` JSON
- `GET /` / `GET /api/info`：返回服务名、版本号（`APP_VERSION`）、Java 版本与主机名

## 构建与自检

```bash
mvn -B -DskipTests package
java -jar target/sunmao-test-jar.jar --self-test
java -jar target/sunmao-test-jar.jar
```

## 发布测试产物

- `Dockerfile`：基于 `maven:3.9-eclipse-temurin-21` 打包 JAR 并运行于 `eclipse-temurin:21-jre`（默认端口 `8080`）
- `deploy/k8s.yaml`：Kubernetes `Namespace` / `Deployment` / `Service` 清单（`sunmao-samples/sunmao-test-jar`）
- `sunmao.yaml`：SunMao 流水线配置（支持 `server` / `container` / `k8s` / `all` 部署目标）

PS C:\Users\31238> java --version
openjdk 21.0.5 2024-10-15 LTS
OpenJDK Runtime Environment Zulu21.38+21-CA (build 21.0.5+11-LTS)
OpenJDK 64-Bit Server VM Zulu21.38+21-CA (build 21.0.5+11-LTS, mixed mode, sharing)

PS C:\Users\31238\Desktop\microservices-practice-2412190233> docker version
Client:
 Version:           29.7.2
 API version:       1.55
 Go version:        go1.26.5
 Git commit:        a7dcaa6
 Built:             Wed Aug  5 18:31:33 2026
 OS/Arch:           windows/amd64
 Context:           desktop-linux

Server: Docker Desktop 4.90.0 (238679)
 Engine:
  Version:          29.7.2
  API version:      1.55 (minimum version 1.40)
  Go version:       go1.26.5
  Git commit:       6a43e3d
  Built:            Wed Aug  5 18:28:36 2026
  OS/Arch:          linux/amd64
  Experimental:     false
 containerd:
  Version:          v2.3.3
  GitCommit:        aad11006b869517fcd3009450b6f82da282e1a9b
 runc:
  Version:          1.4.3
  GitCommit:        v1.4.3-0-gbb14dabe
 docker-init:
  Version:          0.19.0
  GitCommit:        de40ad0

PS C:\Users\31238> git --version
git version 2.28.0.windows.1

PS C:\Users\31238\Desktop\microservices-practice-2412190233> mvn --version
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Maven home: D:\dev\apache-maven-3.9.16
Java version: 21.0.5, vendor: Azul Systems, Inc., runtime: C:\Program Files\Zulu\zulu-21
Default locale: zh_CN, platform encoding: UTF-8
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"

PS C:\Users\31238\Desktop\microservices-practice-2412190233> docker compose version
Docker Compose version v5.5.1

1.能服务独立，独立开发部署，服务间通信，独立扩展，技术栈灵活的一种软件架构模式
2.单体架构技术栈统一，便于开发，但膨胀后难以维护，维服务架构可独立部署，技术栈灵活，便于维护。
3.先理清逻辑，再做拆分，便于对比
4.便于复现结果和调试，保证功能完善
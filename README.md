# AutoBackup

一款开源的自动文件夹备份工具，特别适合于备份游戏服务端，如MC服务端。比起其他备份工具，AutoBackup 具有以下优点：
1. 定时自动备份：用户可以设置每天、每周、每月、每年的备份时间。
2. 自动压缩：备份文件会自动压缩成 zip 格式，名称为原文件夹名加上日期时间戳。
3. 自动删除旧备份：用户可以通过配置文件设置保留的备份文件的数量。
4. 云备份（百度网盘）：用户开启云备份后，AutoBackup 除了备份到本地，还会自动备份到百度网盘。
5. 文件占用：当检测到文件被占用（如 MC 服务端运行时），AutoBackup 会先将文件复制至临时目录再执行备份，无需停止服务。
6. 日志记录：AutoBackup 会记录备份日志，方便用户查看备份情况。
7. 多文件夹备份：用户可以配置多个文件夹进行备份，无需多开 AutoBackup。
8. 无人值守: 仅需一次手动授权，AutoBackup即可在长达 10 年内自动刷新 Token，无需任何人工干预。
9. 失败通知：当备份失败时，AutoBackup 会自动发送邮件通知。
10. 多平台支持：AutoBackup 使用 Java 开发，支持 Windows、Linux、MacOS 等多平台。

## 使用教程
### 1. 配置文件`config.yml`
```yaml
# 云备份配置
云备份: true  # 是否启用云备份功能，true表示启用，false表示禁用

# 百度云存储相关配置
百度SecretKey: Snp2sQwRnSFaayGkZiAjmJu07RGAN5qi  # 在百度开放平台申请的SecretKey，用于身份验证
百度AppKey: ldojhtCoZ3Y966ti9bpPcKECiPNJIRbq      # 在百度开放平台申请的AppKey，与应用关联的标识

# 备份任务列表
备份任务:
  # 第一个备份任务配置
  - 
    cloud: '/apps/存储/server_backups'  # 云备份目标文件夹路径，未开启云备份时，此项无效
    path: 'C:/Users/zhouy/File/kaiFa/测试备份/源文件夹'  # 本地需要备份的源文件夹路径
    number: 3  # 保留的备份文件数量，超过此数量的旧备份将被删除
    corn: '0 * * * * ?'  # 定时备份的cron表达式，这里表示每分钟的第0秒执行一次备份
    target: 'C:/Users/zhouy/File/kaiFa/测试备份/目标文件夹'  # 本地备份的目标文件夹路径
    

# 是否启用备份失败通知（true表示启用，false表示禁用）
备份失败通知: true
# 接收通知邮件的邮箱地址
邮件接收人: 3214948198@qq.com
# 邮件标题
邮件标题: AutoBackup 备份失败！！！
# 邮件服务器端口号（587是QQ邮箱的SSL/TLS端口）
邮件服务器port: 587
# 邮件服务器主机地址（QQ邮箱的SMTP服务器）
邮件服务器host: smtp.qq.com
# 发件人邮箱账号（需要与SMTP服务器匹配）
发送者username: 123@qq.com
# 发件人邮箱密码或授权码（QQ邮箱需要使用授权码而非登录密码）
发送者password: epzsxxaattaccddh

# 认证令牌（自动生成，无需修改）
token: null
```
### 2. 获取百度开放平台凭证
1.  访问 [百度网盘开放平台](https://pan.baidu.com/union/console) 并登录。
2.  完成开发者认证，然后创建一个“个人应用-存储”类型的应用。
3.  在应用详情页，记下您的 **`AppKey`** 和 **`SecretKey`**。

### 3. Cron表达式
1. 使用Cron表达式可以精确地指定每天、每周、每月、每年的备份时间。
2. 可以使用[Cron表达式生成器](https://cron.szzz666.top/)快速生成表达式。

### 4. 启动AutoBackup
1. 下载 [AutoBackup](https://github.com/szzz666/AutoBackup/releases/download/v1.0.0/AutoBackup.zip) 压缩包并解压。
2. Windows平台双击运行 `Start.bat`
3. 安装 Java17 及以上版本运行环境。
4. 运行 `java -jar AutoBackup-1.0-SNAPSHOT.jar`
# common接口

## 接口列表

### 1. MermesLog (统一日志管理类)

- **类路径**: `com.mermes.common.log.MermesLog`
- **描述**: 统一日志打印接口，提供日志级别过滤和发布模式下的内容安全遮蔽。

#### 核心方法

* **v** (VERBOSE 级别)
  - 签名：`fun v(tag: String, msg: String)`
  - 描述：输出最详细的调试日志。在全局最低级别高于 VERBOSE 或发布遮蔽模式下会被完全忽略。

* **d** (DEBUG 级别)
  - 签名：`fun d(tag: String, msg: String)`
  - 描述：输出调试阶段的常规运行信息。在发布遮蔽模式下会被完全忽略。

* **i** (INFO 级别)
  - 签名：`fun i(tag: String, msg: String)`
  - 描述：输出业务流程关键信息。如果是发布级别日志（isRelease = true），会对 msg 内容进行抽象提取，不展示实现相关的具体内容。

* **w** (WARN 级别)
  - 签名：`fun w(tag: String, msg: String, tr: Throwable? = null)`
  - 描述：输出警告日志，可附带异常栈。在发布模式下异常栈会被抽象格式化，不透露详细源码文件名和行数。

* **e** (ERROR 级别)
  - 签名：`fun e(tag: String, msg: String, tr: Throwable? = null)`
  - 描述：输出错误日志与异常栈。发布模式下仅打印错误概要。

* **setLogLevel** (设置全局最低日志级别)
  - 签名：`fun setLogLevel(level: LogLevel)`
  - 参数：
    | 参数名 | 类型 | 说明 |
    |--------|------|------|
    | level  | LogLevel | 目标级别枚举 (VERBOSE, DEBUG, INFO, WARN, ERROR, NONE) |

* **setReleaseMode** (控制发布模式的遮蔽策略)
  - 签名：`fun setReleaseMode(isRelease: Boolean)`
  - 参数：
    | 参数名 | 类型 | 说明 |
    |--------|------|------|
    | isRelease | Boolean | 若为 true，则在 INFO/WARN/ERROR 级日志输出时自动脱敏过滤，低级日志直接静默 |


### 2. MermesCrashHandler (未捕获异常监控类)

- **类路径**: `com.mermes.common.crash.MermesCrashHandler`
- **描述**: 全局 Crash 捕获注册中心，接管 Thread 的异常捕获逻辑并触发用户自定义监听器。

#### 内部接口

* **CrashListener** (崩溃监听接口)
  - 签名：
    ```kotlin
    interface CrashListener {
        fun onCrash(thread: Thread, throwable: Throwable)
    }
    ```

#### 核心方法

* **init** (初始化处理器)
  - 签名：`fun init(context: android.content.Context)`
  - 描述：接管默认的崩溃处理器，并初始化崩溃防护规则。

* **registerCrashListener** (注册自定义 Crash 回调)
  - 签名：`fun registerCrashListener(listener: CrashListener)`
  - 描述：传入用户自定义的回调。当触发未捕获异常时，执行该回调，然后将其递交给系统默认崩溃处理器。

* **unregisterCrashListener** (注销自定义 Crash 回调)
  - 签名：`fun unregisterCrashListener()`
  - 描述：清空用户自定义回调，恢复仅通过系统默认 `UncaughtExceptionHandler` 处理。
